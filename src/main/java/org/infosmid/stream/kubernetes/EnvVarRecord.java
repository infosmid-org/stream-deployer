package org.infosmid.stream.kubernetes;

import org.jspecify.annotations.Nullable;

public record EnvVarRecord(
        String name,
        @Nullable String value,
        @Nullable EnvVarSourceRecord valueFrom
) {
    public EnvVarRecord(String name, String value) {
        this(name, value, null);
    }
}
