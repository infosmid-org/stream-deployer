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
package org.infosmid.stream.diagram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.Map.*;

import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.infosmid.stream.dsl.AppNode;
import org.infosmid.stream.dsl.SinkDestinationNode;
import org.infosmid.stream.dsl.SourceDestinationNode;
import org.infosmid.stream.dsl.StreamNode;
import org.infosmid.stream.dsl.StreamParser;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import org.infosmid.stream.core.StreamDefinition;

/**
 * Generates Mermaid FlowChart diagrams from stream definitions.
 */
public class StreamMermaidGenerator {
    private static final Logger logger = Logger.getLogger(StreamMermaidGenerator.class.getName());

    public static final String THEME_CSS_START = "%%{init: {\"theme\": \"dark\", \"themeCSS\": \""
        + ".label { color: white; font-weight: normal; display: inline-flex; align-items; center; justify-content: center; border-radius: 9999px; padding: 4px 12px; } "
        + ".name { color: lightgrey; font-weight: normal; padding: 4px 12px; } ";

    public static final String THEME_CSS_END = "\"}}%%\n";
    public static final Map<String, String> THEME_NODE_CSS = ofEntries(
        entry("destination",
            ".destination-node { background-color: #17242b; text-align: left; justify-content: left; margin-right: 16px; } .destination-node .name { color: white; font-weight: bold; } .destination-node .label { background-color: #0000ff; }"),
        entry("tap",
            ".tap-node { background-color: #17242b; text-align: left; justify-content: left; margin-right: 16px; } .tap-node .name { color: white; font-weight: bold; } .tap-node .label { background-color: #0000ff; }"),
        entry("source", ".source-node { background-color: #17242b; text-align: left; justify-content: left; margin-right: 16px; } .source-node .label { background-color: #0096ff; } "),
        entry("sink", ".sink-node { background-color: #17242b; text-align: left; justify-content: left; margin-right: 16px; } .sink-node .label { background-color: #f5be00; } "),
        entry("processor", ".processor-node { background-color: #17242b; text-align: left; justify-content: left; margin-right: 16px; } .processor-node .label { background-color: #80b600; } "),
        entry("app", ".app-node { background-color: #17242b; text-align: left; justify-content: left; margin-right: 16px; } .app-node .label { background-color: #ff00de; }")
    );


    @NonNull
    public static String node(@NonNull String name, @NonNull String appName, @NonNull String nodeType) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(nodeType, "nodeType must not be null");
        String lowerType = nodeType.toLowerCase(Locale.ROOT);
        String typeLabel = lowerType.equals("destination") || lowerType.equals("tap") ? lowerType : appName;
        return new StringBuilder()
            .append("<div class='")
            .append(lowerType)
            .append("-node")
            .append("'>")
            .append("<span class='name'>")
            .append(name)
            .append("</span>")
            .append("<br>")
            .append("<span class='label'>")
            .append(typeLabel.toUpperCase(Locale.ROOT))
            .append("</span>")
            .append("</div>")
            .toString();
    }

    @NonNull
    public static String generate(@NonNull StreamDefinition streamDefinition) {
        Objects.requireNonNull(streamDefinition, "streamDefinition must not be null");
        return generate(streamDefinition.getName(), streamDefinition.description(), Collections.singletonList(streamDefinition));
    }

    @NonNull
    public static String generate(@NonNull StreamNode streamNode) {
        Objects.requireNonNull(streamNode, "streamNode must not be null");
        return generateFromNodes(streamNode.getStreamName(), streamNode.getStreamDescription(), Collections.singletonList(streamNode));
    }

    @NonNull
    public static String generate(@Nullable String streamName, String description, @NonNull String dslText) {
        Objects.requireNonNull(dslText, "dslText must not be null");
        StreamNode streamNode = new StreamParser(streamName, description, dslText).parse();
        return generate(streamNode);
    }

    @NonNull
    public static String generate(@NonNull String dslText) {
        return generate(null, null, dslText);
    }

    @NonNull
    public static String generate(String streamName, String description, @NonNull List<StreamDefinition> streamDefinitions) {
        Objects.requireNonNull(streamDefinitions, "streamDefinitions must not be null");
        List<StreamNode> streamNodes = new ArrayList<>(streamDefinitions.size());
        for (StreamDefinition def : streamDefinitions) {
            String name = def.getName();
            String dsl = def.getDsl();
            if (dsl == null) {
                throw new IllegalArgumentException("StreamDefinition dslText must not be null");
            }
            StreamNode node = new StreamParser(name, description, dsl).parse();
            streamNodes.add(node);
        }
        return generateFromNodes(streamName, description, streamNodes);
    }

    @NonNull
    public static String generateFromNodes(String streamName, String description, @NonNull List<StreamNode> streamNodes) {
        Objects.requireNonNull(streamNodes, "streamNodes must not be null");
        boolean useSubgraphs = streamNodes.size() > 1;
        return generateFromNodes(streamName, description, streamNodes, useSubgraphs);
    }

    @NonNull
    public static String generateFromNodes(String inputStreamName, String description, @NonNull List<StreamNode> streamNodes, boolean useSubgraphs) {
        Objects.requireNonNull(streamNodes, "streamNodes must not be null");
        Map<String, String> nodeTypeMappings = new HashMap<>();
        String streamDescription = description;
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");
        Map<String, String> knownApps = new LinkedHashMap<>();
        for (StreamNode sn : streamNodes) {
            String streamName = sn.getStreamName();
            if (streamName != null) {
                for (AppNode app : sn.getAppNodes()) {
                    String key = streamName + "." + app.getLabelName();
                    String nodeId = useSubgraphs
                        ? sanitize(streamName) + "_" + sanitize(app.getLabelName())
                        : sanitize(app.getLabelName());
                    knownApps.put(key, nodeId);
                }
            }
        }

        List<String> tapLinks = new ArrayList<>();
        int linkCount = 0;
        for (StreamNode sn : streamNodes) {
            String streamName = sn.getStreamName();
            String indent = useSubgraphs ? "        " : "    ";

            if (useSubgraphs) {
                String subgraphId = streamName != null ? sanitize(streamName) : "stream_" + Integer.toHexString(sn.hashCode());
                String subgraphTitle = streamName != null ? streamName : sn.getStreamDescription();
                if(sn.getStreamDescription() != null && !sn.getStreamDescription().isBlank()) {
                    subgraphTitle = subgraphTitle != null ? subgraphTitle + " - " + sn.getStreamDescription() : sn.getStreamDescription();
                }
                sb.append("    subgraph ").append(subgraphId).append(" [\"").append(subgraphTitle).append("\"]\n");
            }

            SourceDestinationNode sourceDest = sn.getSourceDestinationNode();
            List<AppNode> appNodes = sn.getAppNodes();
            SinkDestinationNode sinkDest = sn.getSinkDestinationNode();
            int totalApps = appNodes.size();

            String sourceNodeId = null;
            if (sourceDest != null) {
                String destName = sourceDest.getDestinationName();
                boolean isTap = isTap(destName);
                String nodeType = isTap ? "tap" : "destination";

                sourceNodeId = (isTap ? "tap_" : "dest_") + sanitize(destName);
                sb.append(indent).append(sourceNodeId).append("(\"").append(node(destName, sourceDest.getDestinationName(), nodeType)).append("\"):::").append(nodeType).append("\n");
                nodeTypeMappings.put(sourceNodeId, nodeType);
                if (isTap && knownApps.containsKey(destName)) {
                    String targetAppId = knownApps.get(destName);
                    tapLinks.add(targetAppId + " -.-> " + sourceNodeId);
                }
            }

            List<String> appNodeIds = new ArrayList<>(totalApps);
            for (int i = 0; i < totalApps; i++) {
                AppNode app = appNodes.get(i);
                String labelName = app.getLabelName();
                String nodeType = determineAppType(i, totalApps, app.isUnboundStreamApp(), sourceDest != null, sinkDest != null);
                String appId = (useSubgraphs && streamName != null)
                    ? sanitize(streamName) + "_" + sanitize(labelName)
                    : sanitize(labelName);
                appNodeIds.add(appId);
                sb.append(indent).append(appId).append("[\"").append(node(labelName, app.getName(), nodeType)).append("\"]:::").append(nodeType).append("\n");
                nodeTypeMappings.put(appId, nodeType);
            }

            String sinkNodeId = null;
            if (sinkDest != null) {
                String destName = sinkDest.getDestinationName();
                boolean isTap = isTap(destName);
                String nodeType = isTap ? "tap" : "destination";
                sinkNodeId = (isTap ? "tap_" : "dest_") + sanitize(destName);
                sb.append(indent).append(sinkNodeId).append("(\"").append(node(destName, destName, nodeType)).append("\"):::").append(nodeType).append("\n");
                nodeTypeMappings.put(sinkNodeId, nodeType);
            }

            boolean isUnbound = !appNodes.isEmpty() && appNodes.get(0).isUnboundStreamApp();
            if (!isUnbound) {
                if (sourceNodeId != null && !appNodeIds.isEmpty()) {
                    sb.append(indent).append(sourceNodeId).append(" --> ").append(appNodeIds.get(0)).append("\n");
                    linkCount++;
                }
                for (int i = 0; i < totalApps - 1; i++) {
                    sb.append(indent).append(appNodeIds.get(i)).append(" --> ").append(appNodeIds.get(i + 1)).append("\n");
                    linkCount++;
                }
                if (sinkNodeId != null && !appNodeIds.isEmpty()) {
                    sb.append(indent).append(appNodeIds.get(totalApps - 1)).append(" --> ").append(sinkNodeId).append("\n");
                    linkCount++;
                }
                if (appNodeIds.isEmpty() && sourceNodeId != null && sinkNodeId != null) {
                    sb.append(indent).append(sourceNodeId).append(" --> ").append(sinkNodeId).append("\n");
                    linkCount++;
                }
            }

            if (useSubgraphs) {
                sb.append("    end\n");
            }
        }

        List<Integer> tapLinkIndices = new ArrayList<>();
        for (String tapLink : tapLinks) {
            sb.append("    ").append(tapLink).append("\n");
            tapLinkIndices.add(linkCount++);
        }

        sb.append("\n");
        var nodeTypesUsed = new HashSet<>(nodeTypeMappings.values());
        if (nodeTypesUsed.contains("source")) {
            sb.append("    classDef source fill:#17242b,stroke:#0096ff,stroke-width:3px;\n");
        }
        if (nodeTypesUsed.contains("processor")) {
            sb.append("    classDef processor fill:#17242b,stroke:#80b600,stroke-width:3px;\n");
        }
        if (nodeTypesUsed.contains("sink")) {
            sb.append("    classDef sink fill:#17242b,stroke:#f5be00,stroke-width:3px;\n");
        }
        if (nodeTypesUsed.contains("app")) {
            sb.append("    classDef app fill:#17242b,stroke:#ff00de,stroke-width:3px;\n");
        }
        if (nodeTypesUsed.contains("destination")) {
            sb.append("    classDef destination fill:#17242b,stroke:#0000ff,stroke-width:3px;\n");
        }
        if (nodeTypesUsed.contains("tap")) {
            sb.append("    classDef tap fill:#17242b,stroke:#0000ff,stroke-width:3px;\n");
        }
        nodeTypeMappings.forEach((nodeName, nodeType) -> {
            sb.append("    class ");
            sb.append(nodeName);
            sb.append(" ");
            sb.append(nodeType);
            sb.append(";\n");
        });
        if (linkCount > 0) {
            sb.append("\n");
            sb.append("    linkStyle default stroke-width:3px;\n");
            if (!tapLinkIndices.isEmpty()) {
                String indices = tapLinkIndices.stream().map(String::valueOf).collect(Collectors.joining(","));
                sb.append("    linkStyle ").append(indices).append(" stroke-width:1px;\n");
            }
        }
        StringBuilder diagram = new StringBuilder();
        if (inputStreamName != null && !inputStreamName.isBlank()) {
            diagram.append("---\ntitle: ");
            diagram.append(inputStreamName);
            if(streamDescription != null && !streamDescription.isBlank()) {
                diagram.append(" - ");
                diagram.append(streamDescription);
            }
            diagram.append("\n---\n");
        }
        diagram.append(THEME_CSS_START);
        nodeTypesUsed.forEach((nodeType) -> diagram.append(THEME_NODE_CSS.get(nodeType)));
        diagram.append(THEME_CSS_END);
        diagram.append(sb.toString());
        return diagram.toString();
    }

    private static boolean isTap(@Nullable String destinationName) {
        return destinationName != null && destinationName.contains(".");
    }

    @NonNull
    private static String determineAppType(
        int index,
        int totalApps,
        boolean isUnboundStreamApp,
        boolean hasSourceDest,
        boolean hasSinkDest) {
        if (totalApps == 1 && isUnboundStreamApp) {
            return "app";
        }
        if (index == 0) {
            if (!hasSourceDest) {
                return "source";
            } else if (totalApps == 1 && !hasSinkDest) {
                return "sink";
            } else {
                return "processor";
            }
        } else {
            if (index < totalApps - 1) {
                return "processor";
            } else if (hasSinkDest) {
                return "processor";
            } else {
                return "sink";
            }
        }
    }

    @NonNull
    private static String sanitize(@Nullable String id) {
        if (id == null) {
            return "";
        }
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
