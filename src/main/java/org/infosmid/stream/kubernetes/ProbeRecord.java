/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
