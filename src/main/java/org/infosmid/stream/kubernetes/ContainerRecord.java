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
