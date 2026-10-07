package org.infosmid.stream.kubernetes;

import java.util.List;

public record ExecActionRecord(List<String> command) {
    public ExecActionRecord(String... command) {
        this(List.of(command));
    }
}
