package org.infosmid.stream.kubernetes;

import java.util.List;

public record ContainerRecord(
    String name,
    String image,
    List<EnvVarRecord> env,
    List<String> args,
    List<ContainerPortRecord> ports,
    ResourceRequirementsRecord resources,
    ProbeRecord livenessProbe,
    ProbeRecord readinessProbe,
    ProbeRecord startupProbe,
    List<VolumeMountRecord> volumeMounts,
    String imagePullPolicy
) {}
