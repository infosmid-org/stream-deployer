package org.infosmid.stream.kubernetes;

import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

public record ContainerRecord(
    String name,
    String image,
    @Nullable List<EnvVarRecord> env,
    @Nullable List<String> args,
    @Nullable List<ContainerPortRecord> ports,
    @Nullable ResourceRequirementsRecord resources,
    @Nullable ProbeRecord livenessProbe,
    @Nullable ProbeRecord readinessProbe,
    @Nullable ProbeRecord startupProbe,
    @Nullable List<VolumeMountRecord> volumeMounts,
    @Nullable String imagePullPolicy,
    @Nullable List<EnvFromSourceRecord> envFrom
) {
    public ContainerRecord(
        String name,
        String image,
        @Nullable List<EnvVarRecord> env,
        @Nullable List<String> args,
        @Nullable List<ContainerPortRecord> ports,
        @Nullable ResourceRequirementsRecord resources,
        @Nullable ProbeRecord livenessProbe,
        @Nullable ProbeRecord readinessProbe,
        @Nullable ProbeRecord startupProbe,
        @Nullable List<VolumeMountRecord> volumeMounts,
        @Nullable String imagePullPolicy
    ) {
        this(name, image, env, args, ports, resources, livenessProbe, readinessProbe, startupProbe, volumeMounts, imagePullPolicy, Collections.emptyList());
    }
}
