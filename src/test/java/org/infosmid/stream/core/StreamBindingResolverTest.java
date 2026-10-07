package org.infosmid.stream.core;

import java.util.List;
import org.infosmid.stream.dsl.StreamNode;
import org.infosmid.stream.dsl.StreamParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StreamBindingResolverTest {

    private final StreamBindingResolver resolver = new StreamBindingResolver();

    private StreamNode parse(String name, String dsl) {
        return new StreamParser(name, dsl).parse();
    }

    @Test
    public void testScenario1StandardLinearTwoAppPipeline() {
        StreamNode streamNode = parse("time-logger", "time | log");
        List<StreamBindingResolver.ResolvedAppBindings> resolved = resolver.resolve("time-logger", streamNode);

        assertThat(resolved).hasSize(2);

        // App 0: time
        StreamBindingResolver.ResolvedAppBindings timeApp = resolved.get(0);
        assertThat(timeApp.label()).isEqualTo("time");
        assertThat(timeApp.appType()).isEqualTo(StreamBindingResolver.AppType.source);
        assertThat(timeApp.bindingProperties())
                .containsEntry("spring.cloud.dataflow.stream.name", "time-logger")
                .containsEntry("spring.cloud.dataflow.stream.app.label", "time")
                .containsEntry("spring.cloud.dataflow.stream.app.type", "source")
                .containsEntry("spring.cloud.stream.bindings.output.destination", "time-logger.time")
                .containsEntry("spring.cloud.stream.bindings.output.producer.requiredGroups", "time-logger")
                .doesNotContainKey("spring.cloud.stream.bindings.input.destination")
                .doesNotContainKey("spring.cloud.stream.bindings.input.group");
        assertThat(timeApp.literalArguments()).isEmpty();

        // App 1: log
        StreamBindingResolver.ResolvedAppBindings logApp = resolved.get(1);
        assertThat(logApp.label()).isEqualTo("log");
        assertThat(logApp.appType()).isEqualTo(StreamBindingResolver.AppType.sink);
        assertThat(logApp.bindingProperties())
                .containsEntry("spring.cloud.dataflow.stream.name", "time-logger")
                .containsEntry("spring.cloud.dataflow.stream.app.label", "log")
                .containsEntry("spring.cloud.dataflow.stream.app.type", "sink")
                .containsEntry("spring.cloud.stream.bindings.input.destination", "time-logger.time")
                .containsEntry("spring.cloud.stream.bindings.input.group", "time-logger")
                .doesNotContainKey("spring.cloud.stream.bindings.output.destination")
                .doesNotContainKey("spring.cloud.stream.bindings.output.producer.requiredGroups");
        assertThat(logApp.literalArguments()).isEmpty();
    }

    @Test
    public void testScenario2MultiAppPipelineProcessorChain() {
        StreamNode streamNode = parse("multi-stream", "http | filter | transform | log");
        List<StreamBindingResolver.ResolvedAppBindings> resolved = resolver.resolve("multi-stream", streamNode);

        assertThat(resolved).hasSize(4);

        // App 0: http (source)
        StreamBindingResolver.ResolvedAppBindings httpApp = resolved.get(0);
        assertThat(httpApp.label()).isEqualTo("http");
        assertThat(httpApp.appType()).isEqualTo(StreamBindingResolver.AppType.source);
        assertThat(httpApp.bindingProperties())
                .containsEntry("spring.cloud.stream.bindings.output.destination", "multi-stream.http")
                .containsEntry("spring.cloud.stream.bindings.output.producer.requiredGroups", "multi-stream")
                .doesNotContainKey("spring.cloud.stream.bindings.input.destination");

        // App 1: filter (processor)
        StreamBindingResolver.ResolvedAppBindings filterApp = resolved.get(1);
        assertThat(filterApp.label()).isEqualTo("filter");
        assertThat(filterApp.appType()).isEqualTo(StreamBindingResolver.AppType.processor);
        assertThat(filterApp.bindingProperties())
                .containsEntry("spring.cloud.stream.bindings.input.destination", "multi-stream.http")
                .containsEntry("spring.cloud.stream.bindings.input.group", "multi-stream")
                .containsEntry("spring.cloud.stream.bindings.output.destination", "multi-stream.filter")
                .containsEntry("spring.cloud.stream.bindings.output.producer.requiredGroups", "multi-stream");

        // App 2: transform (processor)
        StreamBindingResolver.ResolvedAppBindings transformApp = resolved.get(2);
        assertThat(transformApp.label()).isEqualTo("transform");
        assertThat(transformApp.appType()).isEqualTo(StreamBindingResolver.AppType.processor);
        assertThat(transformApp.bindingProperties())
                .containsEntry("spring.cloud.stream.bindings.input.destination", "multi-stream.filter")
                .containsEntry("spring.cloud.stream.bindings.input.group", "multi-stream")
                .containsEntry("spring.cloud.stream.bindings.output.destination", "multi-stream.transform")
                .containsEntry("spring.cloud.stream.bindings.output.producer.requiredGroups", "multi-stream");

        // App 3: log (sink)
        StreamBindingResolver.ResolvedAppBindings logApp = resolved.get(3);
        assertThat(logApp.label()).isEqualTo("log");
        assertThat(logApp.appType()).isEqualTo(StreamBindingResolver.AppType.sink);
        assertThat(logApp.bindingProperties())
                .containsEntry("spring.cloud.stream.bindings.input.destination", "multi-stream.transform")
                .containsEntry("spring.cloud.stream.bindings.input.group", "multi-stream")
                .doesNotContainKey("spring.cloud.stream.bindings.output.destination");
    }

    @Test
    public void testScenario3SourceToNamedDestinationChannel() {
        StreamNode streamNode = parse("time-publisher", "time > :TIME_LOG");
        List<StreamBindingResolver.ResolvedAppBindings> resolved = resolver.resolve("time-publisher", streamNode);

        assertThat(resolved).hasSize(1);
        StreamBindingResolver.ResolvedAppBindings timeApp = resolved.get(0);
        assertThat(timeApp.label()).isEqualTo("time");
        assertThat(timeApp.appType()).isEqualTo(StreamBindingResolver.AppType.source);
        assertThat(timeApp.bindingProperties())
                .containsEntry("spring.cloud.dataflow.stream.name", "time-publisher")
                .containsEntry("spring.cloud.dataflow.stream.app.label", "time")
                .containsEntry("spring.cloud.dataflow.stream.app.type", "source")
                .containsEntry("spring.cloud.stream.bindings.output.destination", "TIME_LOG")
                .doesNotContainKey("spring.cloud.stream.bindings.output.producer.requiredGroups");
    }

    @Test
    public void testScenario4NamedDestinationChannelToSink() {
        StreamNode streamNode = parse("publish-logs", ":TIME_LOG > log");
        List<StreamBindingResolver.ResolvedAppBindings> resolved = resolver.resolve("publish-logs", streamNode);

        assertThat(resolved).hasSize(1);
        StreamBindingResolver.ResolvedAppBindings logApp = resolved.get(0);
        assertThat(logApp.label()).isEqualTo("log");
        assertThat(logApp.appType()).isEqualTo(StreamBindingResolver.AppType.sink);
        assertThat(logApp.bindingProperties())
                .containsEntry("spring.cloud.dataflow.stream.name", "publish-logs")
                .containsEntry("spring.cloud.dataflow.stream.app.label", "log")
                .containsEntry("spring.cloud.dataflow.stream.app.type", "sink")
                .containsEntry("spring.cloud.stream.bindings.input.destination", "TIME_LOG")
                .containsEntry("spring.cloud.stream.bindings.input.group", "publish-logs")
                .doesNotContainKey("spring.cloud.stream.bindings.output.destination");
    }

    @Test
    public void testScenario5NamedDestinationWithExplicitConsumerGroup() {
        StreamNode streamNode = parse("custom-logs", ":TIME_LOG --group=my_group > log");
        List<StreamBindingResolver.ResolvedAppBindings> resolved = resolver.resolve("custom-logs", streamNode);

        assertThat(resolved).hasSize(1);
        StreamBindingResolver.ResolvedAppBindings logApp = resolved.get(0);
        assertThat(logApp.label()).isEqualTo("log");
        assertThat(logApp.appType()).isEqualTo(StreamBindingResolver.AppType.sink);
        assertThat(logApp.bindingProperties())
                .containsEntry("spring.cloud.stream.bindings.input.destination", "TIME_LOG")
                .containsEntry("spring.cloud.stream.bindings.input.group", "my_group");
    }

    @Test
    public void testScenario6ContentTypeArgumentTranslation() {
        StreamNode streamNode = parse("content-type-stream", "time --outputType=application/json --fixed-delay=5 | log --inputType=application/json --level=WARN");
        List<StreamBindingResolver.ResolvedAppBindings> resolved = resolver.resolve("content-type-stream", streamNode);

        assertThat(resolved).hasSize(2);

        // App 0: time
        StreamBindingResolver.ResolvedAppBindings timeApp = resolved.get(0);
        assertThat(timeApp.bindingProperties())
                .containsEntry("spring.cloud.stream.bindings.output.contentType", "application/json");
        assertThat(timeApp.literalArguments())
                .doesNotContainKey("outputType")
                .containsEntry("fixed-delay", "5");

        // App 1: log
        StreamBindingResolver.ResolvedAppBindings logApp = resolved.get(1);
        assertThat(logApp.bindingProperties())
                .containsEntry("spring.cloud.stream.bindings.input.contentType", "application/json");
        assertThat(logApp.literalArguments())
                .doesNotContainKey("inputType")
                .containsEntry("level", "WARN");
    }

    @Test
    public void testScenario7SingleUnboundStreamApplication() {
        StreamNode streamNode = parse("standalone-app", "timestamp");
        List<StreamBindingResolver.ResolvedAppBindings> resolved = resolver.resolve("standalone-app", streamNode);

        assertThat(resolved).hasSize(1);
        StreamBindingResolver.ResolvedAppBindings app = resolved.get(0);
        assertThat(app.label()).isEqualTo("timestamp");
        assertThat(app.appType()).isEqualTo(StreamBindingResolver.AppType.app);
        assertThat(app.bindingProperties())
                .containsEntry("spring.cloud.dataflow.stream.name", "standalone-app")
                .containsEntry("spring.cloud.dataflow.stream.app.label", "timestamp")
                .containsEntry("spring.cloud.dataflow.stream.app.type", "app")
                .doesNotContainKey("spring.cloud.stream.bindings.input.destination")
                .doesNotContainKey("spring.cloud.stream.bindings.output.destination");
    }

    @Test
    public void testNamedSourceToNamedSinkWithSingleProcessor() {
        StreamNode streamNode = parse("bridge-stream", ":SOURCE_DEST > bridge > :SINK_DEST");
        List<StreamBindingResolver.ResolvedAppBindings> resolved = resolver.resolve("bridge-stream", streamNode);

        assertThat(resolved).hasSize(1);
        StreamBindingResolver.ResolvedAppBindings bridgeApp = resolved.get(0);
        assertThat(bridgeApp.label()).isEqualTo("bridge");
        assertThat(bridgeApp.appType()).isEqualTo(StreamBindingResolver.AppType.processor);
        assertThat(bridgeApp.bindingProperties())
                .containsEntry("spring.cloud.stream.bindings.input.destination", "SOURCE_DEST")
                .containsEntry("spring.cloud.stream.bindings.input.group", "bridge-stream")
                .containsEntry("spring.cloud.stream.bindings.output.destination", "SINK_DEST")
                .doesNotContainKey("spring.cloud.stream.bindings.output.producer.requiredGroups");
    }

    @Test
    public void testNullSafety() {
        assertThatThrownBy(() -> resolver.resolve(null, parse("test", "time | log")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.resolve("test", null))
                .isInstanceOf(NullPointerException.class);
    }
}
