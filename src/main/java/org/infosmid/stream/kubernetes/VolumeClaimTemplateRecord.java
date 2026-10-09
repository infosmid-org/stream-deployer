package org.infosmid.stream.kubernetes;

import java.util.Map;

public record VolumeClaimTemplateRecord(String name, String storageClassName, Map<String, String> storage) {}
