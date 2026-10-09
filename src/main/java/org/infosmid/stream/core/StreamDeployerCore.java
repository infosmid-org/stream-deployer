/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.infosmid.stream.kubernetes.KubernetesPropertyResolver;
import org.infosmid.stream.kubernetes.KubernetesResourceGenerator;

public class StreamDeployerCore {
    private static final Logger logger = Logger.getLogger(StreamDeployerCore.class.getName());

    private final KubernetesResourceGenerator resourceGenerator;
    private final StreamParser streamParser;
    private final StreamBindingResolver streamBindingResolver;
    private final StreamDefinitionValidator streamDefinitionValidator;

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
        this(resourceGenerator, streamParser, streamBindingResolver, new StreamDefinitionValidator(streamParser, streamBindingResolver));
    }

    public StreamDeployerCore(
            @NonNull KubernetesResourceGenerator resourceGenerator,
            @NonNull StreamParser streamParser,
            @NonNull StreamBindingResolver streamBindingResolver,
            @NonNull StreamDefinitionValidator streamDefinitionValidator) {
        this.resourceGenerator = Objects.requireNonNull(resourceGenerator, "resourceGenerator must not be null");
        this.streamParser = Objects.requireNonNull(streamParser, "streamParser must not be null");
        this.streamBindingResolver = Objects.requireNonNull(streamBindingResolver, "streamBindingResolver must not be null");
        this.streamDefinitionValidator = Objects.requireNonNull(streamDefinitionValidator, "streamDefinitionValidator must not be null");
    }

    @NonNull
    public StreamDefinitionValidator getStreamDefinitionValidator() {
        return streamDefinitionValidator;
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

            StreamNode streamNode = streamParser.parse(streamName, streamDef.description(), dslText);
            streamDefinitionValidator.validate(streamName, streamNode, metadataProperties);

            List<StreamBindingResolver.ResolvedAppBindings> resolvedApps =
                    streamBindingResolver.resolve(streamName, streamNode);

            for (StreamBindingResolver.ResolvedAppBindings resolvedApp : resolvedApps) {
                String label = resolvedApp.label();
                AppNode appNode = findAppNode(streamNode, label);
                if (appNode == null) {
                    throw new IllegalStateException("AppNode not found for label: " + label);
                }
                String image = streamDefinitionValidator.resolveImage(
                        streamName, appNode, resolvedApp.appType(), metadataProperties
                );

                // 1. Initialize with generated stream bindings & coordinates (lowest precedence)
                Map<String, String> appProperties = new LinkedHashMap<>(resolvedApp.bindingProperties());

                // 2. Put literal DSL arguments
                appProperties.putAll(resolvedApp.literalArguments());

                // 3. Merge wildcard deployment properties (app.*.)
                mergeProperties(appProperties, metadataProperties, "app.*.");
                mergeProperties(appProperties, deploymentProperties, "app.*.");

                // 4. Merge specific deployment properties (app.<label>.) (highest precedence)
                mergeProperties(appProperties, metadataProperties, "app." + label + ".");
                mergeProperties(appProperties, deploymentProperties, "app." + label + ".");

                // Deployment coordinates and resolved deployer properties
                Map<String, String> appDeploymentProperties = KubernetesPropertyResolver.resolveDeploymentProperties(
                        metadataProperties, deploymentProperties, label
                );
                appDeploymentProperties.put("spring.cloud.deployer.group", streamName);
                appDeploymentProperties.put("spring.cloud.deployer.kubernetes.appName", streamName + "-" + label);

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
