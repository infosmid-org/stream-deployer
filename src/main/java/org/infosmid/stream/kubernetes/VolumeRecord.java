package org.infosmid.stream.kubernetes;

public record VolumeRecord(String name, EmptyDirVolumeSourceRecord emptyDir) {
    public VolumeRecord(String name) {
        this(name, new EmptyDirVolumeSourceRecord(null, null));
    }

    public VolumeRecord(String name, String emptyDir) {
        this(name, emptyDir != null ? new EmptyDirVolumeSourceRecord(null, null) : null);
    }
}
