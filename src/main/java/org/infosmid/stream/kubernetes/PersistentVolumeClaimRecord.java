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

import java.util.List;
import java.util.Map;

public record PersistentVolumeClaimRecord(
    String name,
    String storageClassName,
    List<String> accessModes,
    ResourceRequirementsRecord resources
) {
    public PersistentVolumeClaimRecord(String name, String storageClassName, List<String> accessModes, ResourceRequirementsRecord resources) {
        this.name = name;
        this.storageClassName = storageClassName;
        this.accessModes = accessModes != null ? accessModes : List.of("ReadWriteOnce");
        this.resources = resources;
    }

    public PersistentVolumeClaimRecord(String name, String storageClassName, Map<String, String> storage) {
        this(name, storageClassName, List.of("ReadWriteOnce"), new ResourceRequirementsRecord(null, storage));
    }
}
