package org.infosmid.stream.core;

import org.jspecify.annotations.Nullable;

public record StreamDefinition(String name, String dslText) {
    public @Nullable String getName() {
        return name;
    }

    public @Nullable String getDsl() {
        return dslText;
    }
}
