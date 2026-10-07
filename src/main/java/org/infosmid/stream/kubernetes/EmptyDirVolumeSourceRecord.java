package org.infosmid.stream.kubernetes;

public record EmptyDirVolumeSourceRecord(String medium, String sizeLimit) {
    public EmptyDirVolumeSourceRecord() {
        this(null, null);
    }
}
