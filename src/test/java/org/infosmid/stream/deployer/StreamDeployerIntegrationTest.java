package org.infosmid.stream.deployer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamDeployerIntegrationTest {
    @Test
    public void testTimeLogger() throws Exception {
        Path resourcePath = Paths.get("src", "test", "resources");
        String definition = resourcePath.resolve("time-logger.json").toString();
        String properties = resourcePath.resolve("time-logger.properties").toString();
        String metadata = resourcePath.resolve("stream-metadata.properties").toString();
        String output = "time-logger.yaml";
        String diagram = "time-logger.mmd";

        // Delete output files if exist
        Files.deleteIfExists(Paths.get(output));
        Files.deleteIfExists(Paths.get(diagram));

        StreamDeployerApplication application = new StreamDeployerApplication();
        int exitCode = new CommandLine(application).execute(
            "--definition=" + definition,
            "--properties=" + properties,
            "--metadata=" + metadata,
            "--output=" + output,
            "--diagram=" + diagram
        );

        assertThat(exitCode).isEqualTo(0);

        File outputFile = new File(output);
        assertThat(outputFile).exists();

        File diagramFile = new File(diagram);
        assertThat(diagramFile).exists();
        String diagramContent = Files.readString(diagramFile.toPath());
        assertThat(diagramContent).contains(
            "<div class='source-node'><span class='name'>time</span>",
            "<div class='sink-node'><span class='name'>log</span>"
        );

        List<JsonNode> documents = parseYamlDocuments(outputFile);
        assertThat(documents).hasSize(4);

        // Assertions for time service
        JsonNode timeService = findResource(documents, "Service", "time-logger-time");
        assertThat(timeService.path("kind").asText()).isEqualTo("Service");
        assertThat(timeService.path("metadata").path("name").asText()).isEqualTo("time-logger-time");
        assertThat(getServicePorts(timeService)).contains(8080);

        // Assertions for time deployment
        JsonNode timeDeployment = findResource(documents, "Deployment", "time-logger-time");
        assertThat(timeDeployment.path("kind").asText()).isEqualTo("Deployment");
        assertThat(timeDeployment.path("metadata").path("name").asText()).isEqualTo("time-logger-time");
        assertThat(getContainerPorts(timeDeployment)).contains(8080);

        List<String> timeArgs = getContainerArgs(timeDeployment);
        assertThat(timeArgs).contains(
            "--spring.cloud.dataflow.stream.app.label=time",
            "--spring.cloud.dataflow.stream.app.type=source",
            "--spring.cloud.dataflow.stream.name=time-logger",
            "--spring.cloud.stream.bindings.output.destination=time-logger.time",
            "--spring.cloud.stream.bindings.output.producer.requiredGroups=time-logger"
        );

        // Assertions for log service
        JsonNode logService = findResource(documents, "Service", "time-logger-log");
        assertThat(logService.path("kind").asText()).isEqualTo("Service");
        assertThat(logService.path("metadata").path("name").asText()).isEqualTo("time-logger-log");
        assertThat(getServicePorts(logService)).contains(8080);

        // Assertions for log deployment
        JsonNode logDeployment = findResource(documents, "Deployment", "time-logger-log");
        assertThat(logDeployment.path("kind").asText()).isEqualTo("Deployment");
        assertThat(logDeployment.path("metadata").path("name").asText()).isEqualTo("time-logger-log");
        assertThat(getContainerPorts(logDeployment)).contains(8080);

        List<String> logArgs = getContainerArgs(logDeployment);
        assertThat(logArgs).contains(
            "--spring.cloud.dataflow.stream.app.label=log",
            "--spring.cloud.dataflow.stream.app.type=sink",
            "--spring.cloud.dataflow.stream.name=time-logger",
            "--spring.cloud.stream.bindings.input.destination=time-logger.time",
            "--spring.cloud.stream.bindings.input.group=time-logger"
        );
    }

    @Test
    public void testTimePublisher() throws Exception {
        Path resourcePath = Paths.get("src", "test", "resources");
        String definition = resourcePath.resolve("time-publisher.json").toString();
        String properties = resourcePath.resolve("time-publisher.properties").toString();
        String metadata = resourcePath.resolve("stream-metadata.properties").toString();
        String output = "time-publisher.yaml";
        String diagram = "time-publisher.mmd";

        // Delete output files if exist
        Files.deleteIfExists(Paths.get(output));
        Files.deleteIfExists(Paths.get(diagram));

        StreamDeployerApplication application = new StreamDeployerApplication();
        int exitCode = new CommandLine(application).execute(
                "--definition=" + definition,
                "--properties=" + properties,
                "--metadata=" + metadata,
                "--output=" + output,
                "--diagram=" + diagram
        );

        assertThat(exitCode).isEqualTo(0);

        File outputFile = new File(output);
        assertThat(outputFile).exists();

        File diagramFile = new File(diagram);
        assertThat(diagramFile).exists();
        String diagramContent = Files.readString(diagramFile.toPath());
        assertThat(diagramContent).contains(
            "<div class='source-node'><span class='name'>time</span>",
            "<div class='processor-node'><span class='name'>filter</span>",
            "<div class='destination-node'><span class='name'>TIME_LOG</span>"
        );

        List<JsonNode> documents = parseYamlDocuments(outputFile);
        assertThat(documents).hasSize(4);

        // Assertions for time service
        JsonNode timeService = findResource(documents, "Service", "time-publisher-time");
        assertThat(timeService.path("kind").asText()).isEqualTo("Service");
        assertThat(timeService.path("metadata").path("name").asText()).isEqualTo("time-publisher-time");
        assertThat(getServicePorts(timeService)).contains(8080);

        // Assertions for time deployment
        JsonNode timeDeployment = findResource(documents, "Deployment", "time-publisher-time");
        assertThat(timeDeployment.path("kind").asText()).isEqualTo("Deployment");
        assertThat(timeDeployment.path("metadata").path("name").asText()).isEqualTo("time-publisher-time");
        assertThat(getContainerPorts(timeDeployment)).contains(8080);

        List<String> timeArgs = getContainerArgs(timeDeployment);
        assertThat(timeArgs).contains(
            "--spring.cloud.dataflow.stream.app.label=time",
            "--spring.cloud.dataflow.stream.app.type=source",
            "--spring.cloud.dataflow.stream.name=time-publisher",
            "--spring.cloud.stream.bindings.output.destination=time-publisher.time",
            "--spring.cloud.stream.bindings.output.producer.requiredGroups=time-publisher"
        );

        // Assertions for filter service
        JsonNode filterService = findResource(documents, "Service", "time-publisher-filter");
        assertThat(filterService.path("kind").asText()).isEqualTo("Service");
        assertThat(filterService.path("metadata").path("name").asText()).isEqualTo("time-publisher-filter");
        assertThat(getServicePorts(filterService)).contains(8080);

        // Assertions for filter deployment
        JsonNode filterDeployment = findResource(documents, "Deployment", "time-publisher-filter");
        assertThat(filterDeployment.path("kind").asText()).isEqualTo("Deployment");
        assertThat(filterDeployment.path("metadata").path("name").asText()).isEqualTo("time-publisher-filter");
        assertThat(getContainerPorts(filterDeployment)).contains(8080);

        List<String> filterArgs = getContainerArgs(filterDeployment);
        assertThat(filterArgs).contains(
            "--function.expression=#{payload != null}",
            "--spring.cloud.dataflow.stream.app.label=filter",
            "--spring.cloud.dataflow.stream.app.type=processor",
            "--spring.cloud.dataflow.stream.name=time-publisher",
            "--spring.cloud.stream.bindings.input.destination=time-publisher.time",
            "--spring.cloud.stream.bindings.input.group=time-publisher",
            "--spring.cloud.stream.bindings.output.destination=TIME_LOG"
        );
        assertThat(filterArgs).noneMatch(arg -> arg.startsWith("--spring.cloud.stream.bindings.output.producer.requiredGroups"));
    }

    @Test
    public void testPublishLogs() throws Exception {
        Path resourcePath = Paths.get("src", "test", "resources");
        String definition = resourcePath.resolve("publish-logs.json").toString();
        String properties = resourcePath.resolve("publish-logs.properties").toString();
        String metadata = resourcePath.resolve("stream-metadata.properties").toString();
        String output = "publish-logs.yaml";
        String diagram = "publish-logs.mmd";

        // Delete output files if exist
        Files.deleteIfExists(Paths.get(output));
        Files.deleteIfExists(Paths.get(diagram));

        StreamDeployerApplication application = new StreamDeployerApplication();
        int exitCode = new CommandLine(application).execute(
            "--definition=" + definition,
            "--properties=" + properties,
            "--metadata=" + metadata,
            "--output=" + output,
            "--diagram=" + diagram
        );

        assertThat(exitCode).isEqualTo(0);

        File outputFile = new File(output);
        assertThat(outputFile).exists();

        File diagramFile = new File(diagram);
        assertThat(diagramFile).exists();
        String diagramContent = Files.readString(diagramFile.toPath());
        assertThat(diagramContent).contains(
            "<div class='destination-node'><span class='name'>TIME_LOG</span>",
            "<div class='sink-node'><span class='name'>log</span>"
        );

        List<JsonNode> documents = parseYamlDocuments(outputFile);
        assertThat(documents).hasSize(2);

        // Assertions for log service
        JsonNode logService = findResource(documents, "Service", "publish-logs-log");
        assertThat(logService.path("kind").asText()).isEqualTo("Service");
        assertThat(logService.path("metadata").path("name").asText()).isEqualTo("publish-logs-log");
        assertThat(getServicePorts(logService)).contains(8080);

        // Assertions for log deployment
        JsonNode logDeployment = findResource(documents, "Deployment", "publish-logs-log");
        assertThat(logDeployment.path("kind").asText()).isEqualTo("Deployment");
        assertThat(logDeployment.path("metadata").path("name").asText()).isEqualTo("publish-logs-log");
        assertThat(getContainerPorts(logDeployment)).contains(8080);

        List<String> logArgs = getContainerArgs(logDeployment);
        assertThat(logArgs).contains(
            "--spring.cloud.dataflow.stream.app.label=log",
            "--spring.cloud.dataflow.stream.app.type=sink",
            "--spring.cloud.dataflow.stream.name=publish-logs",
            "--spring.cloud.stream.bindings.input.destination=TIME_LOG",
            "--spring.cloud.stream.bindings.input.group=publish-logs"
        );
    }

    @Test
    public void testDiagramDefaultName() throws Exception {
        Path resourcePath = Paths.get("src", "test", "resources");
        String definition = resourcePath.resolve("time-logger.json").toString();
        String defaultDiagram = "time-logger.mmd";

        Files.deleteIfExists(Paths.get(defaultDiagram));

        StreamDeployerApplication application = new StreamDeployerApplication();
        int exitCode = new CommandLine(application).execute(
            "--definition=" + definition,
            "--diagram"
        );

        assertThat(exitCode).isEqualTo(0);
        File diagramFile = new File(defaultDiagram);
        assertThat(diagramFile).exists();
        String diagramContent = Files.readString(diagramFile.toPath());
        assertThat(diagramContent).contains(
            "<div class='source-node'><span class='name'>time</span>",
            "<div class='sink-node'><span class='name'>log</span>"
        );
    }

    private List<JsonNode> parseYamlDocuments(File file) throws IOException {
        YAMLMapper mapper = new YAMLMapper();
        try (YAMLParser parser = mapper.getFactory().createParser(file)) {
            MappingIterator<JsonNode> iterator = mapper.readValues(parser, JsonNode.class);
            return iterator.readAll();
        }
    }

    private JsonNode findResource(List<JsonNode> documents, String kind, String name) {
        return documents.stream()
                .filter(doc -> kind.equals(doc.path("kind").asText())
                        && name.equals(doc.path("metadata").path("name").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Resource with kind '" + kind + "' and name '" + name + "' not found"));
    }

    private List<Integer> getServicePorts(JsonNode serviceDoc) {
        JsonNode portsNode = serviceDoc.path("spec").path("ports");
        assertThat(portsNode.isArray()).isTrue();
        List<Integer> ports = new ArrayList<>();
        portsNode.forEach(port -> ports.add(port.path("port").asInt()));
        return ports;
    }

    private List<Integer> getContainerPorts(JsonNode deploymentDoc) {
        JsonNode containers = deploymentDoc.path("spec").path("template").path("spec").path("containers");
        assertThat(containers.isArray()).isTrue();
        assertThat(containers.size()).isGreaterThan(0);
        List<Integer> ports = new ArrayList<>();
        containers.get(0).path("ports").forEach(port -> ports.add(port.path("containerPort").asInt()));
        return ports;
    }

    private List<String> getContainerArgs(JsonNode deploymentDoc) {
        JsonNode containers = deploymentDoc.path("spec").path("template").path("spec").path("containers");
        assertThat(containers.isArray()).isTrue();
        assertThat(containers.size()).isGreaterThan(0);
        List<String> args = new ArrayList<>();
        containers.get(0).path("args").forEach(arg -> args.add(arg.asText()));
        return args;
    }
}
