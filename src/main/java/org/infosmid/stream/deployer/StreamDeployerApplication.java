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
package org.infosmid.stream.deployer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.Serialization;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import org.infosmid.stream.core.StreamDefinition;
import org.infosmid.stream.core.StreamDeployerCore;
import org.infosmid.stream.diagram.StreamMermaidGenerator;
import org.infosmid.stream.kubernetes.KubernetesResourceGenerator;
import org.infosmid.stream.kubernetes.KubernetesResourceHelper;

@Command(name = "stream-deployer", mixinStandardHelpOptions = true, version = "3.0.0-SNAPSHOT",
        description = "Generates Kubernetes YAML and Mermaid diagrams from Stream definitions.")
public class StreamDeployerApplication implements Callable<Integer> {

    @Option(names = {"-d","--definition"}, required = true, description = "JSON file with stream definitions")
    private String definitionFile;

    @Option(names = {"-p","--properties"}, required = false, description = "Properties file with deployment properties")
    private String propertiesFile;

    @Option(names = {"-m","--metadata"}, required = false, description = "Properties file with app metadata (images)")
    private String metadataFile;

    @Option(names = {"-o", "--output"}, required = false, description = "Output YAML file")
    private String outputFile;

    @Option(names = {"--diagram"}, arity = "0..1", fallbackValue = "", description = "Mermaid diagram file")
    private String diagramFile;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new StreamDeployerApplication()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        File defFile = new File(definitionFile);
        String baseName = defFile.getName();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex);
        }

        boolean generateOutput = outputFile != null;
        boolean generateDiagram = diagramFile != null;

        if (!generateOutput && !generateDiagram) {
            generateOutput = true;
            generateDiagram = true;
            outputFile = baseName + ".yaml";
            diagramFile = baseName + ".mmd";
        } else if (generateDiagram && diagramFile.isEmpty()) {
            diagramFile = baseName + ".mmd";
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(defFile);
        List<Map<String, String>> streamDefinitionsRaw;
        if (node.isObject() && node.has("streams")) {
            streamDefinitionsRaw = mapper.convertValue(node.get("streams"), new TypeReference<List<Map<String, String>>>() {});
        } else if (node.isArray()) {
            streamDefinitionsRaw = mapper.convertValue(node, new TypeReference<List<Map<String, String>>>() {});
        } else {
            throw new IllegalArgumentException("Invalid stream definition format. Expected a list or an object with 'streams' field.");
        }

        List<StreamDefinition> streamDefinitions = streamDefinitionsRaw.stream()
                .map(m -> new StreamDefinition(m.get("name"), m.get("description"), m.get("dslText")))
                .toList();

        if (generateDiagram) {
            String streamName = streamDefinitions.size() == 1 ? streamDefinitions.getFirst().getName() : baseName;
            String streamDescription = streamDefinitions.getFirst().description();
            String diagram = StreamMermaidGenerator.generate(streamName, streamDescription, streamDefinitions);
            try (FileWriter writer = new FileWriter(diagramFile)) {
                writer.write(diagram);
            }
            System.out.println("Mermaid diagram for " + streamName + " - " + streamDescription + " generated in " + diagramFile);
        }

        if (generateOutput) {
            if (propertiesFile == null || metadataFile == null) {
                throw new IllegalArgumentException("Properties file (-p/--properties) and metadata file (-m/--metadata) are required when generating YAML output.");
            }

            Properties deploymentProperties = new Properties();
            try (FileInputStream fis = new FileInputStream(propertiesFile)) {
                deploymentProperties.load(fis);
            }

            Properties metadataProperties = new Properties();
            try (FileInputStream fis = new FileInputStream(metadataFile)) {
                metadataProperties.load(fis);
            }

            KubernetesResourceGenerator generator = new KubernetesResourceGenerator();
            StreamDeployerCore core = new StreamDeployerCore(generator);

            List<Object> records = core.deployStreams(streamDefinitions, deploymentProperties, metadataProperties);

            StringBuilder yamlOutput = new StringBuilder();
            for (Object record : records) {
                HasMetadata resource = KubernetesResourceHelper.convert(record);
                String yaml = Serialization.asYaml(resource);
                if (yamlOutput.length() > 0 && !yaml.startsWith("---")) {
                    yamlOutput.append("---\n");
                }
                yamlOutput.append(yaml);
            }

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(yamlOutput.toString());
            }

            System.out.println("Kubernetes YAML generated in " + outputFile);
        }

        return 0;
    }
}
