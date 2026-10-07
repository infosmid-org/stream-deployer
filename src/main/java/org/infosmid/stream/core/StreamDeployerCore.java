package org.infosmid.stream.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import org.infosmid.stream.dsl.AppNode;
import org.infosmid.stream.dsl.StreamNode;
import org.infosmid.stream.dsl.StreamParser;
import org.infosmid.stream.kubernetes.KubernetesResourceGenerator;

public class StreamDeployerCore {
    private static final Logger logger = Logger.getLogger(StreamDeployerCore.class.getName());

    private final KubernetesResourceGenerator resourceGenerator;
    private final StreamParser streamParser;
    private final StreamBindingResolver streamBindingResolver;

    public StreamDeployerCore(@NonNull KubernetesResourceGenerator resourceGenerator) {
        this(resourceGenerator, new StreamParser(), new StreamBindingResolver());
    }

    public StreamDeployerCore(
            @NonNull KubernetesResourceGenerator resourceGenerator,
            @NonNull StreamParser streamParser) {
        this(resourceGenerator, streamParser, new StreamBindingResolver());
    }

    public StreamDeployerCore(
            @NonNull KubernetesResourceGenerator resourceGenerator,
            @NonNull StreamBindingResolver streamBindingResolver) {
        this(resourceGenerator, new StreamParser(), streamBindingResolver);
    }

    public StreamDeployerCore(
            @NonNull KubernetesResourceGenerator resourceGenerator,
            @NonNull StreamParser streamParser,
            @NonNull StreamBindingResolver streamBindingResolver) {
        this.resourceGenerator = Objects.requireNonNull(resourceGenerator, "resourceGenerator must not be null");
        this.streamParser = Objects.requireNonNull(streamParser, "streamParser must not be null");
        this.streamBindingResolver = Objects.requireNonNull(streamBindingResolver, "streamBindingResolver must not be null");
    }
    @NonNull
    public List<Object> deployStreams(
            @NonNull List<StreamDefinition> streamDefinitions,
            @Nullable Properties deploymentProperties,
            @Nullable Properties metadataProperties) {
        Objects.requireNonNull(streamDefinitions, "streamDefinitions must not be null");
        List<Object> allResources = new ArrayList<>();

        for (StreamDefinition streamDef : streamDefinitions) {
            String streamName = streamDef.name();
            String dslText = streamDef.dslText();

            logger.fine(() -> String.format("Deploying stream '%s' with DSL: %s", streamName, dslText));

            StreamNode streamNode = streamParser.parse(streamName, dslText);
            List<StreamBindingResolver.ResolvedAppBindings> resolvedApps =
                    streamBindingResolver.resolve(streamName, streamNode);

            for (StreamBindingResolver.ResolvedAppBindings resolvedApp : resolvedApps) {
                String label = resolvedApp.label();
                String image = metadataProperties != null ? metadataProperties.getProperty("app." + label) : null;
                if (image == null && metadataProperties != null) {
                    image = metadataProperties.getProperty(label);
                }
                if (image == null && metadataProperties != null) {
                    AppNode appNode = findAppNode(streamNode, label);
                    if (appNode != null) {
                        image = metadataProperties.getProperty("app." + appNode.getName());
                        if (image == null) {
                            image = metadataProperties.getProperty(appNode.getName());
                        }
                    }
                }
                if (image == null) {
                    throw new IllegalArgumentException("No image metadata found for app: " + label);
                }

                // 1. Initialize with generated stream bindings & coordinates (lowest precedence)
                Map<String, String> appProperties = new LinkedHashMap<>(resolvedApp.bindingProperties());

                // 2. Put literal DSL arguments
                appProperties.putAll(resolvedApp.literalArguments());

                // 3. Merge wildcard deployment properties (app.*.)
                mergeProperties(appProperties, deploymentProperties, "app.*.");
                mergeProperties(appProperties, metadataProperties, "app.*.");

                // 4. Merge specific deployment properties (app.<label>.) (highest precedence)
                mergeProperties(appProperties, deploymentProperties, "app." + label + ".");
                mergeProperties(appProperties, metadataProperties, "app." + label + ".");

                // Deployment coordinates
                Map<String, String> appDeploymentProperties = new LinkedHashMap<>();
                appDeploymentProperties.put("spring.cloud.deployer.group", streamName);
                appDeploymentProperties.put("spring.cloud.deployer.kubernetes.appName", streamName + "-" + label);

                // Merge deployer properties
                mergeDeploymentProperties(deploymentProperties, appDeploymentProperties, label);

                allResources.addAll(resourceGenerator.generateResources(streamName, label, image, appProperties, appDeploymentProperties));
            }
        }
        return allResources;
    }

    @Nullable
    private AppNode findAppNode(@NonNull StreamNode streamNode, @NonNull String label) {
        for (AppNode appNode : streamNode.getAppNodes()) {
            if (label.equals(appNode.getLabelName())) {
                return appNode;
            }
        }
        return null;
    }

    private void mergeProperties(@NonNull Map<String, String> target, @Nullable Properties source, @NonNull String prefix) {
        if (source == null) {
            return;
        }
        for (String key : source.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                target.put(key.substring(prefix.length()), stripQuotes(source.getProperty(key)));
            }
        }
    }

    private void mergeDeploymentProperties(@Nullable Properties source, @NonNull Map<String, String> target, @NonNull String label) {
        if (source == null) {
            return;
        }
        String wildcardPrefix = "deployer.*.";
        String specificPrefix = "deployer." + label + ".";

        for (String key : source.stringPropertyNames()) {
            String value = stripQuotes(source.getProperty(key));
            if (key.startsWith(wildcardPrefix)) {
                String propertyName = key.substring(wildcardPrefix.length());
                String targetKey = propertyName.startsWith("kubernetes.") ? "spring.cloud.deployer." + propertyName : propertyName;
                target.put(targetKey, value);
            }
            if (key.startsWith(specificPrefix)) {
                String propertyName = key.substring(specificPrefix.length());
                String targetKey = propertyName.startsWith("kubernetes.") ? "spring.cloud.deployer." + propertyName : propertyName;
                target.put(targetKey, value);
            }
        }
    }

    @Nullable
    private String stripQuotes(@Nullable String value) {
        if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
