package org.infosmid.stream.kubernetes;

public class KubernetesResourceValidator {
    public static void validate(Object record) {
        if (record instanceof DeploymentRecord r) {
            validateName(r.name());
            if (r.replicas() < 0) throw new IllegalArgumentException("Replicas must be non-negative");
        } else if (record instanceof ServiceRecord r) {
            validateName(r.name());
        } else if (record instanceof StatefulSetRecord r) {
            validateName(r.name());
            if (r.replicas() < 0) throw new IllegalArgumentException("Replicas must be non-negative");
        } else if (record instanceof PersistentVolumeClaimRecord r) {
            validateName(r.name());
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Resource name cannot be null or empty");
        }
        if (!name.matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?")) {
            throw new IllegalArgumentException("Invalid resource name: " + name + ". Must be a valid DNS subdomain.");
        }
    }
}
