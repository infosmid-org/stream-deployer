package org.infosmid.stream.kubernetes;

import java.util.List;
import java.util.Map;

public record ServiceRecord(
    String name,
    String type,
    Map<String, String> labels,
    Map<String, String> annotations,
    Map<String, String> selector,
    List<ServicePortRecord> ports
) {}
