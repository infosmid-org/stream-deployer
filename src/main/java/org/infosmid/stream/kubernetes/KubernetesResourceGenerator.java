package org.infosmid.stream.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KubernetesResourceGenerator {

    public List<Object> generateResources(String streamName, String label, String image,
                                          Map<String, String> appProperties, Map<String, String> deploymentProperties) {
        List<Object> resources = new ArrayList<>();

        String appId = createDeploymentId(streamName, label);

        // Service
        ServiceRecord service = createService(appId, streamName, label, appProperties, deploymentProperties);
        KubernetesResourceValidator.validate(service);
        resources.add(service);

        // Deployment or StatefulSet
        boolean indexed = Boolean.parseBoolean(deploymentProperties.getOrDefault("spring.cloud.deployer.indexed", "false"));
        if (indexed) {
            StatefulSetRecord statefulSet = createStatefulSet(appId, streamName, label, image, appProperties, deploymentProperties);
            KubernetesResourceValidator.validate(statefulSet);
            resources.add(statefulSet);
        } else {
            DeploymentRecord deployment = createDeployment(appId, streamName, label, image, appProperties, deploymentProperties);
            KubernetesResourceValidator.validate(deployment);
            resources.add(deployment);
        }

        return resources;
    }

    private String createDeploymentId(String streamName, String label) {
        String deploymentId = (streamName + "-" + label);
        return deploymentId.replace('.', '-').toLowerCase();
    }

    private ServiceRecord createService(String appId, String streamName, String label, Map<String, String> appProperties, Map<String, String> deploymentProperties) {
        String serviceName = appId;
        String type = "ClusterIP";
        if ("true".equalsIgnoreCase(getDeploymentProperty(deploymentProperties, "create-load-balancer"))) {
            type = "LoadBalancer";
        } else if (getDeploymentProperty(deploymentProperties, "create-node-port") != null) {
            type = "NodePort";
        }

        Map<String, String> labels = createIdMap(appId, streamName);
        Map<String, String> selector = new HashMap<>(labels);

        int port = Integer.parseInt(appProperties.getOrDefault("server.port", "8080"));
        Integer nodePort = null;
        String nodePortProp = getDeploymentProperty(deploymentProperties, "create-node-port");
        if (nodePortProp != null && !"true".equalsIgnoreCase(nodePortProp)) {
            nodePort = Integer.parseInt(nodePortProp);
        }

        ServicePortRecord servicePort = new ServicePortRecord("port-" + port, port, port, nodePort);

        return new ServiceRecord(serviceName, type, labels, Collections.emptyMap(), selector, Collections.singletonList(servicePort));
    }

    private DeploymentRecord createDeployment(String appId, String streamName, String label, String image,
                                              Map<String, String> appProperties, Map<String, String> deploymentProperties) {
        Map<String, String> labels = createIdMap(appId, streamName);
        int replicas = Integer.parseInt(deploymentProperties.getOrDefault("spring.cloud.deployer.count", "1"));

        PodSpecRecord podSpec = createPodSpec(appId, streamName, image, appProperties, deploymentProperties);

        return new DeploymentRecord(appId, replicas, labels, labels, Collections.emptyMap(), podSpec);
    }

    private StatefulSetRecord createStatefulSet(String appId, String streamName, String label, String image,
                                                Map<String, String> appProperties, Map<String, String> deploymentProperties) {
        Map<String, String> labels = createIdMap(appId, streamName);
        int replicas = Integer.parseInt(deploymentProperties.getOrDefault("spring.cloud.deployer.count", "1"));

        PodSpecRecord podSpec = createPodSpec(appId, streamName, image, appProperties, deploymentProperties);

        return new StatefulSetRecord(appId, replicas, appId, labels, labels, Collections.emptyMap(), podSpec, Collections.emptyList());
    }

    private PodSpecRecord createPodSpec(String appId, String streamName, String image, Map<String, String> appProperties, Map<String, String> deploymentProperties) {
        List<EnvVarRecord> env = new ArrayList<>();
        
        String envVars = getDeploymentProperty(deploymentProperties, "environment-variables");
        if (envVars != null) {
            for (String envVar : envVars.split(",")) {
                String[] parts = envVar.split("=", 2);
                if (parts.length == 2) {
                    env.add(new EnvVarRecord(parts[0].trim(), parts[1].trim()));
                }
            }
        }
        
        env.add(new EnvVarRecord("SPRING_CLOUD_APPLICATION_GROUP", streamName));
        env.add(new EnvVarRecord("SPRING_DEPLOYMENT_ID", appId));

        List<String> args = new ArrayList<>();
        appProperties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> args.add("--" + e.getKey() + "=" + e.getValue()));

        int port = Integer.parseInt(appProperties.getOrDefault("server.port", "8080"));
        ContainerPortRecord containerPort = new ContainerPortRecord("port-" + port, port);

        Map<String, String> limits = new HashMap<>();
        String limitCpu = getDeploymentProperty(deploymentProperties, "limits.cpu");
        if (limitCpu != null) limits.put("cpu", limitCpu);
        String limitMemory = getDeploymentProperty(deploymentProperties, "limits.memory");
        if (limitMemory != null) limits.put("memory", limitMemory);

        Map<String, String> requests = new HashMap<>();
        String requestCpu = getDeploymentProperty(deploymentProperties, "requests.cpu");
        if (requestCpu != null) requests.put("cpu", requestCpu);
        String requestMemory = getDeploymentProperty(deploymentProperties, "requests.memory");
        if (requestMemory != null) requests.put("memory", requestMemory);

        String imagePullPolicy = getDeploymentProperty(deploymentProperties, "image-pull-policy");
        if (imagePullPolicy != null) {
            imagePullPolicy = switch (imagePullPolicy.toUpperCase()) {
                case "ALWAYS" -> "Always";
                case "NEVER" -> "Never";
                case "IF_NOT_PRESENT", "IFNOTPRESENT" -> "IfNotPresent";
                default -> imagePullPolicy;
            };
        } else {
            imagePullPolicy = "IfNotPresent";
        }

        ContainerRecord container = new ContainerRecord(
            appId, image, env, args, Collections.singletonList(containerPort),
            new ResourceRequirementsRecord(limits, requests),
            null, null, null, Collections.emptyList(), imagePullPolicy
        );

        List<String> imagePullSecrets = new ArrayList<>();
        String secrets = getDeploymentProperty(deploymentProperties, "image-pull-secrets");
        if (secrets != null) {
            for (String secret : secrets.split(",")) {
                imagePullSecrets.add(secret.trim());
            }
        }

        return new PodSpecRecord(Collections.singletonList(container), Collections.emptyList(), imagePullSecrets, "Always", null, null);
    }

    private String getDeploymentProperty(Map<String, String> deploymentProperties, String suffix) {
        String fullKey = "spring.cloud.deployer.kubernetes." + suffix;
        if (deploymentProperties.containsKey(fullKey)) {
            return deploymentProperties.get(fullKey);
        }
        String camelKey = "spring.cloud.deployer.kubernetes." + toCamelCase(suffix);
        if (deploymentProperties.containsKey(camelKey)) {
            return deploymentProperties.get(camelKey);
        }
        return null;
    }

    private String toCamelCase(String hyphenated) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : hyphenated.toCharArray()) {
            if (c == '-') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private Map<String, String> createIdMap(String appId, String streamName) {
        Map<String, String> map = new HashMap<>();
        map.put("spring-app-id", appId);
        map.put("spring-group-id", streamName);
        map.put("spring-deployment-id", appId);
        map.put("role", "spring-app");
        return map;
    }
}
