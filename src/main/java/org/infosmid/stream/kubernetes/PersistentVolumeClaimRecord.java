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
