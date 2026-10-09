package org.infosmid.stream.core;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.infosmid.stream.dsl.AppNode;
import org.infosmid.stream.dsl.StreamNode;
import org.infosmid.stream.dsl.StreamParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StreamDefinitionValidatorTest {

    private final StreamDefinitionValidator validator = new StreamDefinitionValidator();

    private StreamNode parse(String name, String dsl) {
        return new StreamParser(name, null, dsl).parse();
    }

    @Test
    public void testValidLinearStreamSourceProcessorSink() {
        StreamNode streamNode = parse("pipeline", "time | filter | log");

        Properties metadata = new Properties();
        metadata.setProperty("app.source.time", "image-time");
        metadata.setProperty("app.processor.filter", "image-filter");
        metadata.setProperty("app.sink.log", "image-log");

        validator.validate("pipeline", streamNode, metadata);

        AppNode timeNode = streamNode.getAppNodes().get(0);
        AppNode filterNode = streamNode.getAppNodes().get(1);
        AppNode logNode = streamNode.getAppNodes().get(2);

        assertThat(validator.resolveImage("pipeline", timeNode, StreamBindingResolver.AppType.source, metadata))
                .isEqualTo("image-time");
        assertThat(validator.resolveImage("pipeline", filterNode, StreamBindingResolver.AppType.processor, metadata))
                .isEqualTo("image-filter");
        assertThat(validator.resolveImage("pipeline", logNode, StreamBindingResolver.AppType.sink, metadata))
                .isEqualTo("image-log");
    }

    @Test
    public void testValidSourceToNamedDestination() {
        StreamNode streamNode = parse("publisher", "time > :TIME_LOG");

        Properties metadata = new Properties();
        metadata.setProperty("app.source.time", "image-time");

        validator.validate("publisher", streamNode, metadata);

        AppNode timeNode = streamNode.getAppNodes().get(0);
        assertThat(validator.resolveImage("publisher", timeNode, StreamBindingResolver.AppType.source, metadata))
                .isEqualTo("image-time");
    }

    @Test
    public void testValidNamedDestinationToSink() {
        StreamNode streamNode = parse("consumer", ":TIME_LOG > log");

        Properties metadata = new Properties();
        metadata.setProperty("app.sink.log", "image-log");

        validator.validate("consumer", streamNode, metadata);

        AppNode logNode = streamNode.getAppNodes().get(0);
        assertThat(validator.resolveImage("consumer", logNode, StreamBindingResolver.AppType.sink, metadata))
                .isEqualTo("image-log");
    }

    @Test
    public void testValidBridgeDestinationProcessor() {
        StreamNode streamNode = parse("bridge", ":IN > filter > :OUT");

        Properties metadata = new Properties();
        metadata.setProperty("app.processor.filter", "image-filter");

        validator.validate("bridge", streamNode, metadata);

        AppNode filterNode = streamNode.getAppNodes().get(0);
        assertThat(validator.resolveImage("bridge", filterNode, StreamBindingResolver.AppType.processor, metadata))
                .isEqualTo("image-filter");
    }

    @Test
    public void testTypeMismatchSourceUsedAsSink() {
        StreamNode streamNode = parse("inverted", "log | time");

        Properties metadata = new Properties();
        metadata.setProperty("app.source.time", "image-time");
        metadata.setProperty("app.sink.log", "image-log");

        assertThatThrownBy(() -> validator.validate("inverted", streamNode, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Application 'log' in stream 'inverted' is defined as type 'sink', but is used as 'source'.");
    }

    @Test
    public void testTypeMismatchSinkUsedAsSource() {
        StreamNode streamNode = parse("bad-sink", "sink1: log | sink2: log");

        Properties metadata = new Properties();
        metadata.setProperty("app.sink.log", "image-log");

        assertThatThrownBy(() -> validator.validate("bad-sink", streamNode, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Application 'sink1' in stream 'bad-sink' is defined as type 'sink', but is used as 'source'.");
    }

    @Test
    public void testTypeMismatchProcessorUsedAsSource() {
        StreamNode streamNode = parse("proc-source", "filter | log");

        Properties metadata = new Properties();
        metadata.setProperty("app.processor.filter", "image-filter");
        metadata.setProperty("app.sink.log", "image-log");

        assertThatThrownBy(() -> validator.validate("proc-source", streamNode, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Application 'filter' in stream 'proc-source' is defined as type 'processor', but is used as 'source'.");
    }

    @Test
    public void testTypeMismatchProcessorUsedAsSink() {
        StreamNode streamNode = parse("proc-sink", "time | filter");

        Properties metadata = new Properties();
        metadata.setProperty("app.source.time", "image-time");
        metadata.setProperty("app.processor.filter", "image-filter");

        assertThatThrownBy(() -> validator.validate("proc-sink", streamNode, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Application 'filter' in stream 'proc-sink' is defined as type 'processor', but is used as 'sink'.");
    }

    @Test
    public void testTypeMismatchSinkUsedAsProcessor() {
        StreamNode streamNode = parse("sink-proc", "time | log | other");

        Properties metadata = new Properties();
        metadata.setProperty("app.source.time", "image-time");
        metadata.setProperty("app.sink.log", "image-log");
        metadata.setProperty("app.sink.other", "image-other");

        assertThatThrownBy(() -> validator.validate("sink-proc", streamNode, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Application 'log' in stream 'sink-proc' is defined as type 'sink', but is used as 'processor'.");
    }

    @Test
    public void testTypeMismatchSourceUsedAsProcessor() {
        StreamNode streamNode = parse("source-proc", "t1: time | t2: time | log");

        Properties metadata = new Properties();
        metadata.setProperty("app.source.time", "image-time");
        metadata.setProperty("app.sink.log", "image-log");

        assertThatThrownBy(() -> validator.validate("source-proc", streamNode, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Application 't2' in stream 'source-proc' is defined as type 'source', but is used as 'processor'.");
    }

    @Test
    public void testLabelOverrideMatching() {
        StreamNode streamNode = parse("label-stream", "customTime: time | log");

        Properties metadata = new Properties();
        metadata.setProperty("app.source.time", "default-time");
        metadata.setProperty("app.source.customTime", "custom-time");
        metadata.setProperty("app.sink.log", "image-log");

        validator.validate("label-stream", streamNode, metadata);

        AppNode timeNode = streamNode.getAppNodes().get(0);
        assertThat(validator.resolveImage("label-stream", timeNode, StreamBindingResolver.AppType.source, metadata))
                .isEqualTo("custom-time");
    }

    @Test
    public void testLabelFallbackToDefinitionName() {
        StreamNode streamNode = parse("label-stream", "customTime: time | log");

        Properties metadata = new Properties();
        metadata.setProperty("app.source.time", "default-time");
        metadata.setProperty("app.sink.log", "image-log");

        validator.validate("label-stream", streamNode, metadata);

        AppNode timeNode = streamNode.getAppNodes().get(0);
        assertThat(validator.resolveImage("label-stream", timeNode, StreamBindingResolver.AppType.source, metadata))
                .isEqualTo("default-time");
    }

    @Test
    public void testLabelTypeMismatch() {
        StreamNode streamNode = parse("label-mismatch", "customTime: time | log");

        Properties metadata = new Properties();
        metadata.setProperty("app.sink.customTime", "sink-time");
        metadata.setProperty("app.sink.log", "image-log");

        assertThatThrownBy(() -> validator.validate("label-mismatch", streamNode, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Application 'customTime' in stream 'label-mismatch' is defined as type 'sink', but is used as 'source'.");
    }

    @Test
    public void testStrictFormatRejectsUntypedProperties() {
        StreamNode streamNode = parse("untyped-stream", "time | log");

        Properties metadata = new Properties();
        metadata.setProperty("app.time", "image-time");
        metadata.setProperty("app.log", "image-log");

        assertThatThrownBy(() -> validator.validate("untyped-stream", streamNode, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No image metadata found for app: time");
    }

    @Test
    public void testUnsupportedAppTypeUnboundStreamApp() {
        StreamNode streamNode = parse("unbound", "timestamp");

        assertThatThrownBy(() -> validator.validate("unbound", streamNode, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Application 'timestamp' in stream 'unbound' has unsupported type 'app'. Supported types are: source, processor, sink.");
    }

    @Test
    public void testMissingImageMetadataThrows() {
        StreamNode streamNode = parse("missing", "time | log");
        Properties metadata = new Properties();

        assertThatThrownBy(() -> validator.validate("missing", streamNode, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No image metadata found for app: time");
    }

    @Test
    public void testQuotesStrippingInImageProperty() {
        StreamNode streamNode = parse("quoted", "time | log");

        Properties metadata = new Properties();
        metadata.setProperty("app.source.time", "\"springcloudstream/time-source-rabbit:main\"");
        metadata.setProperty("app.sink.log", "\"springcloudstream/log-sink-rabbit:main\"");

        AppNode timeNode = streamNode.getAppNodes().get(0);
        assertThat(validator.resolveImage("quoted", timeNode, StreamBindingResolver.AppType.source, metadata))
                .isEqualTo("springcloudstream/time-source-rabbit:main");
    }

    @Test
    public void testNullSafety() {
        StreamNode node = parse("test", "time | log");
        assertThatThrownBy(() -> validator.validate("test", (StreamNode) null, new Properties()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> validator.validate((String) null, node, new Properties()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> validator.validate((StreamDefinition) null, new Properties()))
                .isInstanceOf(NullPointerException.class);
    }
}
