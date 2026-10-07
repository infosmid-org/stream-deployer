package org.infosmid.stream.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.jspecify.annotations.NonNull;
import org.infosmid.stream.dsl.AppNode;
import org.infosmid.stream.dsl.ArgumentNode;
import org.infosmid.stream.dsl.SinkDestinationNode;
import org.infosmid.stream.dsl.SourceDestinationNode;
import org.infosmid.stream.dsl.StreamNode;

public class StreamBindingResolver {
    private static final Logger logger = Logger.getLogger(StreamBindingResolver.class.getName());

    public enum AppType {
        source,
        processor,
        sink,
        app
    }

    public record ResolvedAppBindings(
            @NonNull String label,
            @NonNull AppType appType,
            @NonNull Map<String, String> bindingProperties,
            @NonNull Map<String, String> literalArguments
    ) {
        public ResolvedAppBindings {
            Objects.requireNonNull(label, "label must not be null");
            Objects.requireNonNull(appType, "appType must not be null");
            bindingProperties = Collections.unmodifiableMap(new LinkedHashMap<>(bindingProperties));
            literalArguments = Collections.unmodifiableMap(new LinkedHashMap<>(literalArguments));
        }

        @NonNull
        @Override
        public String label() {
            return label;
        }

        @NonNull
        @Override
        public AppType appType() {
            return appType;
        }

        @NonNull
        @Override
        public Map<String, String> bindingProperties() {
            return bindingProperties;
        }

        @NonNull
        @Override
        public Map<String, String> literalArguments() {
            return literalArguments;
        }
    }

    @NonNull
    public List<ResolvedAppBindings> resolve(@NonNull String streamName, @NonNull StreamNode streamNode) {
        Objects.requireNonNull(streamName, "streamName must not be null");
        Objects.requireNonNull(streamNode, "streamNode must not be null");

        List<AppNode> appNodes = streamNode.getAppNodes();
        if (appNodes == null || appNodes.isEmpty()) {
            return Collections.emptyList();
        }

        int totalApps = appNodes.size();
        SourceDestinationNode sourceDest = streamNode.getSourceDestinationNode();
        SinkDestinationNode sinkDest = streamNode.getSinkDestinationNode();

        List<ResolvedAppBindings> result = new ArrayList<>(totalApps);

        for (int i = 0; i < totalApps; i++) {
            AppNode appNode = appNodes.get(i);
            String label = appNode.getLabelName();

            AppType appType = determineAppType(
                    i,
                    totalApps,
                    appNode.isUnboundStreamApp(),
                    sourceDest != null,
                    sinkDest != null
            );

            logger.fine(() -> String.format(
                    "Resolving bindings for stream '%s', app label '%s', appType '%s'",
                    streamName, label, appType
            ));

            Map<String, String> bindings = new LinkedHashMap<>();
            bindings.put("spring.cloud.dataflow.stream.name", streamName);
            bindings.put("spring.cloud.dataflow.stream.app.label", label);
            bindings.put("spring.cloud.dataflow.stream.app.type", appType.name());

            // Input Binding Resolution
            if (appType == AppType.processor || appType == AppType.sink) {
                if (i == 0 && sourceDest != null) {
                    String destinationName = sourceDest.getDestinationName();
                    bindings.put("spring.cloud.stream.bindings.input.destination", destinationName);

                    String groupValue = streamName;
                    ArgumentNode[] sourceArgs = sourceDest.getArguments();
                    if (sourceArgs != null) {
                        for (ArgumentNode arg : sourceArgs) {
                            if ("group".equals(arg.getName())) {
                                groupValue = arg.getValue();
                                break;
                            }
                        }
                    }
                    bindings.put("spring.cloud.stream.bindings.input.group", groupValue);
                } else if (i > 0) {
                    String upstreamLabel = appNodes.get(i - 1).getLabelName();
                    bindings.put("spring.cloud.stream.bindings.input.destination", streamName + "." + upstreamLabel);
                    bindings.put("spring.cloud.stream.bindings.input.group", streamName);
                }
            }

            // Output Binding Resolution
            if (appType == AppType.source || appType == AppType.processor) {
                if (i == totalApps - 1 && sinkDest != null) {
                    String destinationName = sinkDest.getDestinationName();
                    bindings.put("spring.cloud.stream.bindings.output.destination", destinationName);
                } else {
                    bindings.put("spring.cloud.stream.bindings.output.destination", streamName + "." + label);
                    bindings.put("spring.cloud.stream.bindings.output.producer.requiredGroups", streamName);
                }
            }

            // Content-Type Translation & Literal Argument Filtering
            Map<String, String> literalArguments = new LinkedHashMap<>();
            ArgumentNode[] appArgs = appNode.getArguments();
            if (appArgs != null) {
                for (ArgumentNode arg : appArgs) {
                    if ("inputType".equals(arg.getName())) {
                        bindings.put("spring.cloud.stream.bindings.input.contentType", arg.getValue());
                    } else if ("outputType".equals(arg.getName())) {
                        bindings.put("spring.cloud.stream.bindings.output.contentType", arg.getValue());
                    } else {
                        literalArguments.put(arg.getName(), arg.getValue());
                    }
                }
            }

            result.add(new ResolvedAppBindings(label, appType, bindings, literalArguments));
        }

        return Collections.unmodifiableList(result);
    }

    @NonNull
    private AppType determineAppType(
            int index,
            int totalApps,
            boolean isUnboundStreamApp,
            boolean hasSourceDest,
            boolean hasSinkDest) {
        if (totalApps == 1 && isUnboundStreamApp) {
            return AppType.app;
        }
        if (index == 0) {
            if (!hasSourceDest) {
                return AppType.source;
            } else if (totalApps == 1 && !hasSinkDest) {
                return AppType.sink;
            } else {
                return AppType.processor;
            }
        } else {
            if (index < totalApps - 1) {
                return AppType.processor;
            } else if (hasSinkDest) {
                return AppType.processor;
            } else {
                return AppType.sink;
            }
        }
    }
}
