package org.infosmid.stream.core;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import org.infosmid.stream.core.StreamBindingResolver.AppType;
import org.infosmid.stream.dsl.AppNode;
import org.infosmid.stream.dsl.StreamNode;
import org.infosmid.stream.dsl.StreamParser;

/**
 * Validates stream definitions against application metadata.
 * Ensures application usage in a stream matches the defined application type.
 */
public class StreamDefinitionValidator {
    private static final Logger logger = Logger.getLogger(StreamDefinitionValidator.class.getName());

    public static final Set<String> SUPPORTED_TYPES = Set.of("source", "processor", "sink");

    private final StreamParser streamParser;
    private final StreamBindingResolver streamBindingResolver;

    public StreamDefinitionValidator() {
        this(new StreamParser(), new StreamBindingResolver());
    }

    public StreamDefinitionValidator(
            @NonNull StreamParser streamParser,
            @NonNull StreamBindingResolver streamBindingResolver) {
        this.streamParser = Objects.requireNonNull(streamParser, "streamParser must not be null");
        this.streamBindingResolver = Objects.requireNonNull(streamBindingResolver, "streamBindingResolver must not be null");
    }

    @NonNull
    public StreamParser getStreamParser() {
        return streamParser;
    }

    @NonNull
    public StreamBindingResolver getStreamBindingResolver() {
        return streamBindingResolver;
    }

    /**
     * Validates a list of stream definitions against application metadata.
     *
     * @param streamDefinitions the stream definitions to validate
     * @param metadataProperties the metadata properties containing image definitions
     */
    public void validate(
            @NonNull List<StreamDefinition> streamDefinitions,
            @Nullable Properties metadataProperties) {
        Objects.requireNonNull(streamDefinitions, "streamDefinitions must not be null");
        for (StreamDefinition streamDef : streamDefinitions) {
            validate(streamDef, metadataProperties);
        }
    }

    /**
     * Validates a stream definition against application metadata.
     *
     * @param streamDefinition the stream definition to validate
     * @param metadataProperties the metadata properties containing image definitions
     */
    public void validate(
            @NonNull StreamDefinition streamDefinition,
            @Nullable Properties metadataProperties) {
        Objects.requireNonNull(streamDefinition, "streamDefinition must not be null");
        String streamName = streamDefinition.getName();
        String dslText = streamDefinition.getDsl();
        if (dslText == null) {
            throw new IllegalArgumentException("StreamDefinition dslText must not be null");
        }
        StreamNode streamNode = streamParser.parse(streamName, streamDefinition.description(), dslText);
        validate(streamName, streamNode, metadataProperties);
    }

    /**
     * Validates a parsed stream node against application metadata.
     *
     * @param streamName the name of the stream
     * @param streamNode the parsed stream node
     * @param metadataProperties the metadata properties containing image definitions
     */
    public void validate(
            @NonNull String streamName,
            @NonNull StreamNode streamNode,
            @Nullable Properties metadataProperties) {
        Objects.requireNonNull(streamName, "streamName must not be null");
        Objects.requireNonNull(streamNode, "streamNode must not be null");

        List<StreamBindingResolver.ResolvedAppBindings> resolvedApps =
                streamBindingResolver.resolve(streamName, streamNode);

        List<AppNode> appNodes = streamNode.getAppNodes();
        if (appNodes == null || appNodes.isEmpty()) {
            return;
        }

        for (int i = 0; i < resolvedApps.size(); i++) {
            StreamBindingResolver.ResolvedAppBindings resolvedApp = resolvedApps.get(i);
            AppNode appNode = appNodes.get(i);
            validateAppType(streamName, appNode, resolvedApp.appType());
            if (metadataProperties != null) {
                resolveImage(streamName, appNode, resolvedApp.appType(), metadataProperties);
            }
        }
    }

    /**
     * Validates that the application type is one of the supported types.
     *
     * @param streamName the name of the stream
     * @param appNode the application node in the stream
     * @param actualType the topological type of the application
     */
    public void validateAppType(
            @NonNull String streamName,
            @NonNull AppNode appNode,
            @NonNull AppType actualType) {
        Objects.requireNonNull(streamName, "streamName must not be null");
        Objects.requireNonNull(appNode, "appNode must not be null");
        Objects.requireNonNull(actualType, "actualType must not be null");

        if (actualType != AppType.source && actualType != AppType.processor && actualType != AppType.sink) {
            throw new IllegalArgumentException(String.format(
                    "Application '%s' in stream '%s' has unsupported type '%s'. Supported types are: source, processor, sink.",
                    appNode.getLabelName(), streamName, actualType
            ));
        }
    }

    /**
     * Resolves the container image URL for an application and validates its type.
     *
     * @param streamName the name of the stream
     * @param appNode the application node in the stream
     * @param actualType the topological type of the application
     * @param metadataProperties the metadata properties containing image definitions
     * @return the resolved container image URL
     */
    @NonNull
    public String resolveImage(
            @NonNull String streamName,
            @NonNull AppNode appNode,
            @NonNull AppType actualType,
            @Nullable Properties metadataProperties) {
        Objects.requireNonNull(streamName, "streamName must not be null");
        Objects.requireNonNull(appNode, "appNode must not be null");
        Objects.requireNonNull(actualType, "actualType must not be null");

        validateAppType(streamName, appNode, actualType);

        if (metadataProperties == null) {
            throw new IllegalArgumentException("No image metadata found for app: " + appNode.getLabelName());
        }

        String label = appNode.getLabelName();
        String appName = appNode.getName();
        String typeName = actualType.name().toLowerCase(Locale.ROOT);

        // 1. Look up image for actualType: label first, then appName
        String image = findTypedProperty(metadataProperties, typeName, label);
        if (image == null) {
            image = findTypedProperty(metadataProperties, typeName, appName);
        }

        if (image != null && !image.isBlank()) {
            return stripQuotes(image.trim());
        }

        // 2. Check if the application is defined under a different type
        for (String otherTypeName : SUPPORTED_TYPES) {
            if (!otherTypeName.equals(typeName)) {
                String otherImage = findTypedProperty(metadataProperties, otherTypeName, label);
                if (otherImage == null) {
                    otherImage = findTypedProperty(metadataProperties, otherTypeName, appName);
                }
                if (otherImage != null && !otherImage.isBlank()) {
                    throw new IllegalArgumentException(String.format(
                            "Application '%s' in stream '%s' is defined as type '%s', but is used as '%s'.",
                            label, streamName, otherTypeName, typeName
                    ));
                }
            }
        }

        // 3. No image property found for this application under any supported type
        throw new IllegalArgumentException("No image metadata found for app: " + label);
    }

    @Nullable
    private String findTypedProperty(@NonNull Properties properties, @NonNull String type, @NonNull String identifier) {
        return properties.getProperty("app." + type + "." + identifier);
    }

    @NonNull
    private String stripQuotes(@NonNull String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
