package org.infosmid.stream.kubernetes;

public record HTTPGetActionRecord(
    String path,
    Integer port,
    String host,
    String scheme
) {
    public HTTPGetActionRecord(String path, Integer port) {
        this(path, port, null, null);
    }
}
