package org.infosmid.stream.kubernetes;

import java.util.List;
import java.util.Map;

public record StatefulSetRecord(
    String name,
    int replicas,
    String serviceName,
    Map<String, String> labels,
    Map<String, String> podLabels,
    Map<String, String> podAnnotations,
    PodSpecRecord podSpec,
    List<PersistentVolumeClaimRecord> volumeClaimTemplates
) {}
