package org.infosmid.stream.kubernetes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.ExecActionBuilder;
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpecBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.TCPSocketActionBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.VolumeResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;

public class KubernetesResourceHelper {

    public static HasMetadata convert(Object record) {
        if (record instanceof DeploymentRecord r) {
            return convertDeployment(r);
        } else if (record instanceof ServiceRecord r) {
            return convertService(r);
        } else if (record instanceof StatefulSetRecord r) {
            return convertStatefulSet(r);
        } else if (record instanceof PersistentVolumeClaimRecord r) {
            return convertPersistentVolumeClaim(r);
        }
        throw new IllegalArgumentException("Unsupported record type: " + record.getClass());
    }

    private static Deployment convertDeployment(DeploymentRecord r) {
        return new DeploymentBuilder()
                .withNewMetadata()
                    .withName(r.name())
                    .withLabels(r.labels())
                .endMetadata()
                .withNewSpec()
                    .withReplicas(r.replicas())
                    .withNewSelector()
                        .withMatchLabels(r.podLabels())
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .withLabels(r.podLabels())
                            .withAnnotations(r.podAnnotations())
                        .endMetadata()
                        .withSpec(convertPodSpec(r.podSpec()))
                    .endTemplate()
                .endSpec()
                .build();
    }

    private static Service convertService(ServiceRecord r) {
        List<ServicePort> ports = Collections.emptyList();
        if (r.ports() != null) {
            ports = r.ports().stream()
                    .map(p -> new ServicePortBuilder()
                            .withName(p.name())
                            .withPort(p.port())
                            .withNewTargetPort(p.targetPort() != null ? p.targetPort() : p.port())
                            .withNodePort(p.nodePort())
                            .build())
                    .collect(Collectors.toList());
        }

        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(r.name())
                    .withLabels(r.labels())
                    .withAnnotations(r.annotations())
                .endMetadata()
                .withNewSpec()
                    .withType(r.type())
                    .withSelector(r.selector())
                    .withPorts(ports)
                .endSpec()
                .build();
    }

    private static StatefulSet convertStatefulSet(StatefulSetRecord r) {
        StatefulSetSpecBuilder specBuilder = new StatefulSetSpecBuilder()
                .withReplicas(r.replicas())
                .withServiceName(r.serviceName())
                .withNewSelector()
                    .withMatchLabels(r.podLabels())
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .withLabels(r.podLabels())
                        .withAnnotations(r.podAnnotations())
                    .endMetadata()
                        .withSpec(convertPodSpec(r.podSpec()))
                .endTemplate();

        if (r.volumeClaimTemplates() != null && !r.volumeClaimTemplates().isEmpty()) {
            specBuilder.withVolumeClaimTemplates(r.volumeClaimTemplates().stream()
                    .map(KubernetesResourceHelper::convertPersistentVolumeClaim)
                    .collect(Collectors.toList()));
        }

        return new StatefulSetBuilder()
                .withNewMetadata()
                    .withName(r.name())
                    .withLabels(r.labels())
                .endMetadata()
                .withSpec(specBuilder.build())
                .build();
    }

    public static PersistentVolumeClaim convertPersistentVolumeClaim(PersistentVolumeClaimRecord r) {
        PersistentVolumeClaimBuilder pvcb = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                    .withName(r.name())
                .endMetadata();

        PersistentVolumeClaimSpecBuilder specBuilder = new PersistentVolumeClaimSpecBuilder();
        if (r.storageClassName() != null) {
            specBuilder.withStorageClassName(r.storageClassName());
        }
        if (r.accessModes() != null && !r.accessModes().isEmpty()) {
            specBuilder.withAccessModes(r.accessModes());
        }
        if (r.resources() != null) {
            VolumeResourceRequirementsBuilder vrr = new VolumeResourceRequirementsBuilder();
            if (r.resources().requests() != null) {
                vrr.withRequests(r.resources().requests().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new Quantity(e.getValue()))));
            }
            if (r.resources().limits() != null) {
                vrr.withLimits(r.resources().limits().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new Quantity(e.getValue()))));
            }
            specBuilder.withResources(vrr.build());
        }
        pvcb.withSpec(specBuilder.build());
        return pvcb.build();
    }

    private static PodSpec convertPodSpec(PodSpecRecord r) {
        if (r == null) {
            return null;
        }

        PodSpecBuilder builder = new PodSpecBuilder()
                .withRestartPolicy(r.restartPolicy())
                .withServiceAccountName(r.serviceAccountName())
                .withTerminationGracePeriodSeconds(r.terminationGracePeriodSeconds());

        if (r.imagePullSecrets() != null) {
            r.imagePullSecrets().forEach(builder::addNewImagePullSecret);
        }

        if (r.volumes() != null && !r.volumes().isEmpty()) {
            builder.withVolumes(r.volumes().stream()
                    .map(KubernetesResourceHelper::convertVolume)
                    .collect(Collectors.toList()));
        }

        if (r.containers() != null) {
            List<Container> containers = r.containers().stream()
                    .map(c -> {
                        ContainerBuilder cb = new ContainerBuilder()
                                .withName(c.name())
                                .withImage(c.image())
                                .withImagePullPolicy(c.imagePullPolicy());

                        if (c.env() != null) {
                            cb.withEnv(c.env().stream()
                                    .map(KubernetesResourceHelper::convertEnvVar)
                                    .collect(Collectors.toList()));
                        }

                        if (c.envFrom() != null && !c.envFrom().isEmpty()) {
                            cb.withEnvFrom(c.envFrom().stream()
                                    .map(KubernetesResourceHelper::convertEnvFrom)
                                    .collect(Collectors.toList()));
                        }

                        if (c.args() != null) {
                            cb.withArgs(c.args());
                        }

                        if (c.ports() != null) {
                            cb.withPorts(c.ports().stream()
                                    .map(p -> new ContainerPortBuilder()
                                            .withName(p.name())
                                            .withContainerPort(p.containerPort())
                                            .build())
                                    .collect(Collectors.toList()));
                        }

                        if (c.resources() != null) {
                            cb.withResources(convertResources(c.resources()));
                        }

                        if (c.volumeMounts() != null && !c.volumeMounts().isEmpty()) {
                            cb.withVolumeMounts(c.volumeMounts().stream()
                                    .map(KubernetesResourceHelper::convertVolumeMount)
                                    .collect(Collectors.toList()));
                        }

                        if (c.livenessProbe() != null) {
                            cb.withLivenessProbe(convertProbe(c.livenessProbe()));
                        }
                        if (c.readinessProbe() != null) {
                            cb.withReadinessProbe(convertProbe(c.readinessProbe()));
                        }
                        if (c.startupProbe() != null) {
                            cb.withStartupProbe(convertProbe(c.startupProbe()));
                        }

                        return cb.build();
                    })
                    .collect(Collectors.toList());

            builder.withContainers(containers);
        }

        return builder.build();
    }

    private static EnvVar convertEnvVar(EnvVarRecord e) {
        if (e.valueFrom() != null) {
            EnvVarSourceBuilder evsb = new EnvVarSourceBuilder();
            if (e.valueFrom().secretKeyRef() != null) {
                evsb.withNewSecretKeyRef()
                        .withName(e.valueFrom().secretKeyRef().name())
                        .withKey(e.valueFrom().secretKeyRef().key())
                        .endSecretKeyRef();
            } else if (e.valueFrom().configMapKeyRef() != null) {
                evsb.withNewConfigMapKeyRef()
                        .withName(e.valueFrom().configMapKeyRef().name())
                        .withKey(e.valueFrom().configMapKeyRef().key())
                        .endConfigMapKeyRef();
            }
            return new EnvVar(e.name(), e.value(), evsb.build());
        }
        return new EnvVar(e.name(), e.value(), null);
    }

    private static EnvFromSource convertEnvFrom(EnvFromSourceRecord r) {
        EnvFromSourceBuilder builder = new EnvFromSourceBuilder();
        if (r.secretRef() != null) {
            builder.withNewSecretRef()
                    .withName(r.secretRef().name())
                    .endSecretRef();
        } else if (r.configMapRef() != null) {
            builder.withNewConfigMapRef()
                    .withName(r.configMapRef().name())
                    .endConfigMapRef();
        }
        return builder.build();
    }

    private static Volume convertVolume(VolumeRecord r) {
        VolumeBuilder vb = new VolumeBuilder().withName(r.name());
        if (r.emptyDir() != null) {
            EmptyDirVolumeSourceBuilder edb = new EmptyDirVolumeSourceBuilder();
            if (r.emptyDir().medium() != null) {
                edb.withMedium(r.emptyDir().medium());
            }
            if (r.emptyDir().sizeLimit() != null) {
                edb.withNewSizeLimit(r.emptyDir().sizeLimit());
            }
            vb.withEmptyDir(edb.build());
        }
        return vb.build();
    }

    private static VolumeMount convertVolumeMount(VolumeMountRecord r) {
        return new VolumeMountBuilder()
                .withName(r.name())
                .withMountPath(r.mountPath())
                .withReadOnly(r.readOnly())
                .build();
    }

    private static Probe convertProbe(ProbeRecord r) {
        if (r == null) {
            return null;
        }
        ProbeBuilder pb = new ProbeBuilder();
        if (r.initialDelaySeconds() != null) {
            pb.withInitialDelaySeconds(r.initialDelaySeconds());
        }
        if (r.periodSeconds() != null) {
            pb.withPeriodSeconds(r.periodSeconds());
        }
        if (r.timeoutSeconds() != null) {
            pb.withTimeoutSeconds(r.timeoutSeconds());
        }
        if (r.failureThreshold() != null) {
            pb.withFailureThreshold(r.failureThreshold());
        }
        if (r.successThreshold() != null) {
            pb.withSuccessThreshold(r.successThreshold());
        }

        if (r.httpGet() != null) {
            HTTPGetActionBuilder httpBuilder = new HTTPGetActionBuilder();
            if (r.httpGet().path() != null) {
                httpBuilder.withPath(r.httpGet().path());
            }
            if (r.httpGet().port() != null) {
                httpBuilder.withNewPort(r.httpGet().port());
            }
            if (r.httpGet().host() != null) {
                httpBuilder.withHost(r.httpGet().host());
            }
            if (r.httpGet().scheme() != null) {
                httpBuilder.withScheme(r.httpGet().scheme());
            }
            pb.withHttpGet(httpBuilder.build());
        } else if (r.exec() != null) {
            ExecActionBuilder execBuilder = new ExecActionBuilder();
            if (r.exec().command() != null) {
                execBuilder.withCommand(r.exec().command());
            }
            pb.withExec(execBuilder.build());
        } else if (r.tcpSocket() != null) {
            TCPSocketActionBuilder tcpBuilder = new TCPSocketActionBuilder();
            if (r.tcpSocket().port() != null) {
                tcpBuilder.withNewPort(r.tcpSocket().port());
            }
            if (r.tcpSocket().host() != null) {
                tcpBuilder.withHost(r.tcpSocket().host());
            }
            pb.withTcpSocket(tcpBuilder.build());
        }
        return pb.build();
    }

    private static ResourceRequirements convertResources(ResourceRequirementsRecord r) {
        ResourceRequirementsBuilder builder = new ResourceRequirementsBuilder();
        if (r.limits() != null) {
            builder.withLimits(r.limits().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> new Quantity(e.getValue()))));
        }
        if (r.requests() != null) {
            builder.withRequests(r.requests().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> new Quantity(e.getValue()))));
        }
        return builder.build();
    }
}
