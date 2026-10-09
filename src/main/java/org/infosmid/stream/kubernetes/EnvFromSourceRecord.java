package org.infosmid.stream.kubernetes;

import org.jspecify.annotations.Nullable;

public record EnvFromSourceRecord(
        @Nullable SecretEnvSourceRecord secretRef,
        @Nullable ConfigMapEnvSourceRecord configMapRef
) {
    public static EnvFromSourceRecord ofSecret(String name) {
        return new EnvFromSourceRecord(new SecretEnvSourceRecord(name), null);
    }

    public static EnvFromSourceRecord ofConfigMap(String name) {
        return new EnvFromSourceRecord(null, new ConfigMapEnvSourceRecord(name));
    }
}
