package org.infosmid.stream.kubernetes;

public record TCPSocketActionRecord(
    Integer port,
    String host
) {
    public TCPSocketActionRecord(Integer port) {
        this(port, null);
    }
}
