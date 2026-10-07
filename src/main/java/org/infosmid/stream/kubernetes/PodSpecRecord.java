package org.infosmid.stream.kubernetes;

import java.util.List;

public record PodSpecRecord(
    List<ContainerRecord> containers,
    List<VolumeRecord> volumes,
    List<String> imagePullSecrets,
    String restartPolicy,
    String serviceAccountName,
    Long terminationGracePeriodSeconds
) {}
