package org.infosmid.stream.kubernetes;

import java.util.List;

public record ProbeRecord(
    HTTPGetActionRecord httpGet,
    ExecActionRecord exec,
    TCPSocketActionRecord tcpSocket,
    Integer initialDelaySeconds,
    Integer periodSeconds,
    Integer timeoutSeconds,
    Integer failureThreshold,
    Integer successThreshold
) {
    public static ProbeRecord http(String path, int port) {
        return new ProbeRecord(new HTTPGetActionRecord(path, port), null, null, null, null, null, null, null);
    }

    public static ProbeRecord exec(List<String> command) {
        return new ProbeRecord(null, new ExecActionRecord(command), null, null, null, null, null, null);
    }

    public static ProbeRecord tcp(int port) {
        return new ProbeRecord(null, null, new TCPSocketActionRecord(port), null, null, null, null, null);
    }
}
