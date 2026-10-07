package org.infosmid.stream.kubernetes;

import java.util.Map;

public record DeploymentRecord(
    String name,
    int replicas,
    Map<String, String> labels,
    Map<String, String> podLabels,
    Map<String, String> podAnnotations,
    PodSpecRecord podSpec
) {}
