package org.infosmid.stream.kubernetes;

import org.jspecify.annotations.Nullable;

public record EnvVarSourceRecord(
        @Nullable SecretKeySelectorRecord secretKeyRef,
        @Nullable ConfigMapKeySelectorRecord configMapKeyRef
) {
    public static EnvVarSourceRecord ofSecretKey(String name, String key) {
        return new EnvVarSourceRecord(new SecretKeySelectorRecord(name, key), null);
    }

    public static EnvVarSourceRecord ofConfigMapKey(String name, String key) {
        return new EnvVarSourceRecord(null, new ConfigMapKeySelectorRecord(name, key));
    }
}
