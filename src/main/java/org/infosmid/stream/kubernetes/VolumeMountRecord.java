package org.infosmid.stream.kubernetes;

public record VolumeMountRecord(String name, String mountPath, boolean readOnly) {}
