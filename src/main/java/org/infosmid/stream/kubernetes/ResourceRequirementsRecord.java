package org.infosmid.stream.kubernetes;

import java.util.Map;

public record ResourceRequirementsRecord(Map<String, String> limits, Map<String, String> requests) {}
