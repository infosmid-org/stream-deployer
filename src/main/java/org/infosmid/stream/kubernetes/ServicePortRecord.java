package org.infosmid.stream.kubernetes;

public record ServicePortRecord(String name, int port, Integer targetPort, Integer nodePort) {}
