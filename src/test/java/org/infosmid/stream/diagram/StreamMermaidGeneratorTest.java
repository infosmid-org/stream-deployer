package org.infosmid.stream.diagram;

import java.util.List;

import org.infosmid.stream.diagram.StreamMermaidGenerator;
import org.infosmid.stream.dsl.StreamNode;
import org.junit.jupiter.api.Test;

import org.infosmid.stream.core.StreamDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StreamMermaidGeneratorTest {

    @Test
    public void testSimpleSourceToSink() {
        String diagram = StreamMermaidGenerator.generate("time | log");

        assertThat(diagram).contains("flowchart LR\n");
        assertThat(diagram).contains("time[\"<div class='source-node'><span class='name'>time</span><br><span class='label'>TIME</span></div>\"]:::source");
        assertThat(diagram).contains("log[\"<div class='sink-node'><span class='name'>log</span><br><span class='label'>LOG</span></div>\"]:::sink");
        assertThat(diagram).contains("time --> log");
        assertThat(diagram).contains("classDef source fill:#17242b,stroke:#0096ff,stroke-width:3px;");
        assertThat(diagram).contains("classDef sink fill:#17242b,stroke:#f5be00,stroke-width:3px;");
        assertThat(diagram).contains("linkStyle default stroke-width:3px;");
        assertThat(diagram).contains("%%{init: {\"theme\": \"dark\", \"themeCSS\": ");
    }

    @Test
    public void testMultiAppStream() {
        String diagram = StreamMermaidGenerator.generate("time | filter | transform | log");

        assertThat(diagram).contains("time[\"<div class='source-node'><span class='name'>time</span><br><span class='label'>TIME</span></div>\"]:::source");
        assertThat(diagram).contains(
            "filter[\"<div class='processor-node'><span class='name'>filter</span><br><span class='label'>FILTER</span></div>\"]:::processor");
        assertThat(diagram).contains(
            "transform[\"<div class='processor-node'><span class='name'>transform</span><br><span class='label'>TRANSFORM</span></div>\"]:::processor");
        assertThat(diagram).contains("log[\"<div class='sink-node'><span class='name'>log</span><br><span class='label'>LOG</span></div>\"]:::sink");
        assertThat(diagram).contains("time --> filter");
        assertThat(diagram).contains("filter --> transform");
        assertThat(diagram).contains("transform --> log");
    }

    @Test
    public void testSourceToNamedDestination() {
        String diagram = StreamMermaidGenerator.generate("time | filter > :TIME_LOG");

        assertThat(diagram).contains("time[\"<div class='source-node'><span class='name'>time</span><br><span class='label'>TIME</span></div>\"]:::source");
        assertThat(diagram).contains(
            "filter[\"<div class='processor-node'><span class='name'>filter</span><br><span class='label'>FILTER</span></div>\"]:::processor");
        assertThat(diagram).contains("dest_TIME_LOG(\"<div class='destination-node'><span class='name'>TIME_LOG</span><br><span class='label'>DESTINATION</span></div>\"):::destination");
        assertThat(diagram).contains("time --> filter");
        assertThat(diagram).contains("filter --> dest_TIME_LOG");
    }

    @Test
    public void testNamedDestinationToSink() {
        String diagram = StreamMermaidGenerator.generate(":TIME_LOG > log");

        assertThat(diagram).contains("dest_TIME_LOG(\"<div class='destination-node'><span class='name'>TIME_LOG</span><br><span class='label'>DESTINATION</span></div>\"):::destination");
        assertThat(diagram).contains("log[\"<div class='sink-node'><span class='name'>log</span><br><span class='label'>LOG</span></div>\"]:::sink");
        assertThat(diagram).contains("dest_TIME_LOG --> log");
    }

    @Test
    public void testTapStream() {
        String diagram = StreamMermaidGenerator.generate(":stream1.time > log");

        assertThat(diagram).contains("tap_stream1_time(\"<div class='tap-node'><span class='name'>stream1.time</span><br><span class='label'>TAP</span></div>\"):::tap");
        assertThat(diagram).contains("log[\"<div class='sink-node'><span class='name'>log</span><br><span class='label'>LOG</span></div>\"]:::sink");
        assertThat(diagram).contains("tap_stream1_time --> log");
    }

    @Test
    public void testBridgeStream() {
        String diagram = StreamMermaidGenerator.generate(":SOURCE_DEST > bridge > :SINK_DEST");

        assertThat(diagram).contains("dest_SOURCE_DEST(\"<div class='destination-node'><span class='name'>SOURCE_DEST</span><br><span class='label'>DESTINATION</span></div>\"):::destination");
        assertThat(diagram).contains(
            "bridge[\"<div class='processor-node'><span class='name'>bridge</span><br><span class='label'>BRIDGE</span></div>\"]:::processor");
        assertThat(diagram).contains("dest_SINK_DEST(\"<div class='destination-node'><span class='name'>SINK_DEST</span><br><span class='label'>DESTINATION</span></div>\"):::destination");
        assertThat(diagram).contains("dest_SOURCE_DEST --> bridge");
        assertThat(diagram).contains("bridge --> dest_SINK_DEST");
    }

    @Test
    public void testStandaloneApp() {
        String diagram = StreamMermaidGenerator.generate("timestamp");

        assertThat(diagram).contains(
            "timestamp[\"<div class='app-node'><span class='name'>timestamp</span><br><span class='label'>TIMESTAMP</span></div>\"]:::app");
        assertThat(diagram).doesNotContain("-->");
        assertThat(diagram).doesNotContain("linkStyle");
    }

    @Test
    public void testMultipleStreamsReconciliation() {
        List<StreamDefinition> streams = List.of(
            new StreamDefinition("time-publisher", "time publisher", "time | filter > :TIME_LOG"),
            new StreamDefinition("publish-logs", "publish logs", ":TIME_LOG > log")
        );

        String diagram = StreamMermaidGenerator.generate("time-logger", "time logger", streams);

        assertThat(diagram).contains("subgraph time_publisher [\"time-publisher\"]");
        assertThat(diagram).contains("subgraph publish_logs [\"publish-logs\"]");
        assertThat(diagram).contains("dest_TIME_LOG(\"<div class='destination-node'><span class='name'>TIME_LOG</span><br><span class='label'>DESTINATION</span></div>\"):::destination");
        assertThat(diagram).contains("time_publisher_filter --> dest_TIME_LOG");
        assertThat(diagram).contains("dest_TIME_LOG --> publish_logs_log");
    }

    @Test
    public void testMultipleStreamsWithTap() {
        List<StreamDefinition> streams = List.of(
            new StreamDefinition("stream1", "time logger", "time | log"),
            new StreamDefinition("stream2", "time logger", ":stream1.time > log")
        );

        String diagram = StreamMermaidGenerator.generate("time-logger", "time logger", streams);

        assertThat(diagram).contains("subgraph stream1 [\"stream1\"]");
        assertThat(diagram).contains("subgraph stream2 [\"stream2\"]");
        assertThat(diagram).contains("stream1_time -.-> tap_stream1_time");
        assertThat(diagram).contains("tap_stream1_time --> stream2_log");
        assertThat(diagram).contains("linkStyle default stroke-width:3px;");
        assertThat(diagram).contains("linkStyle 2 stroke-width:1px;");
    }

    @Test
    public void testNodeHelper() {
        String result = StreamMermaidGenerator.node("myApp", "myApp", "source");
        assertThat(result).isEqualTo("<div class='source-node'><span class='name'>myApp</span><br><span class='label'>MYAPP</span></div>");

        String destResult = StreamMermaidGenerator.node("myDest", "myDest", "DESTINATION");
        assertThat(destResult).isEqualTo("<div class='destination-node'><span class='name'>myDest</span><br><span class='label'>DESTINATION</span></div>");
    }

    @Test
    public void testCssStylesIncluded() {
        String diagram = StreamMermaidGenerator.generate("time | filter | log");

        assertThat(diagram).doesNotContain(".destination-node");
        assertThat(diagram).doesNotContain(".tap-node");
        assertThat(diagram).contains(".source-node");
        assertThat(diagram).contains(".sink-node");
        assertThat(diagram).contains(".processor-node");
        assertThat(diagram).doesNotContain(".app-node");
        assertThat(diagram).contains(".name {");
        assertThat(diagram).contains(".label {");
        assertThat(diagram).contains("padding: 4px");
        assertThat(diagram).doesNotContain(".destination-node .name, .tap-node .name");
        assertThat(diagram).contains(".source-node .label { background-color: #0096ff; }");
        assertThat(diagram).contains(".processor-node .label { background-color: #80b600; }");
        assertThat(diagram).contains(".sink-node .label { background-color: #f5be00; }");
        assertThat(diagram).doesNotContain(".app-node .label { background-color: #ff00de; }");
        assertThat(diagram).doesNotContain(".destination-node .label { background-color: #0000ff; }");
        assertThat(diagram).doesNotContain(".tap-node .label { background-color: #0000ff; }");
    }

    @Test
    public void testNullSafety() {
        assertThatThrownBy(() -> StreamMermaidGenerator.generate((StreamDefinition) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StreamMermaidGenerator.generate((StreamNode) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StreamMermaidGenerator.generate(null, null, (List<StreamDefinition>) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StreamMermaidGenerator.generateFromNodes(null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
