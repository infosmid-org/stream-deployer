package org.infosmid.stream.kubernetes;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class KubernetesResourceHelperTest {

    @Test
    public void testDeploymentWithVolumesMountsAndProbes() {
        EmptyDirVolumeSourceRecord emptyDir = new EmptyDirVolumeSourceRecord("Memory", "500Mi");
        VolumeRecord volume = new VolumeRecord("cache-vol", emptyDir);
        VolumeMountRecord mount = new VolumeMountRecord("cache-vol", "/cache", false);

        ProbeRecord liveness = new ProbeRecord(
                new HTTPGetActionRecord("/healthz", 8080, "localhost", "HTTP"),
                null, null, 10, 5, 2, 3, 1
        );
        ProbeRecord readiness = new ProbeRecord(
                null,
                new ExecActionRecord("cat", "/tmp/ready"),
                null, 5, 10, 1, 2, 1
        );
        ProbeRecord startup = new ProbeRecord(
                null, null,
                new TCPSocketActionRecord(8080, "localhost"),
                0, 2, 1, 30, 1
        );

        ContainerRecord container = new ContainerRecord(
                "my-app", "my-image:latest",
                List.of(new EnvVarRecord("FOO", "BAR")),
                List.of("--test=true"),
                List.of(new ContainerPortRecord("http", 8080)),
                new ResourceRequirementsRecord(Map.of("cpu", "1"), Map.of("cpu", "500m")),
                liveness, readiness, startup,
                List.of(mount), "Always"
        );

        PodSpecRecord podSpec = new PodSpecRecord(
                List.of(container),
                List.of(volume),
                List.of("regcred"),
                "Always", null, 30L
        );

        DeploymentRecord deployment = new DeploymentRecord(
                "test-deploy", 2,
                Map.of("app", "test"),
                Map.of("app", "test"),
                Map.of("test-annotation", "value"),
                podSpec
        );

        HasMetadata result = KubernetesResourceHelper.convert(deployment);
        assertThat(result).isInstanceOf(Deployment.class);

        Deployment dep = (Deployment) result;
        assertThat(dep.getMetadata().getName()).isEqualTo("test-deploy");
        assertThat(dep.getSpec().getReplicas()).isEqualTo(2);

        // Verify PodSpec volumes
        var volumes = dep.getSpec().getTemplate().getSpec().getVolumes();
        assertThat(volumes).hasSize(1);
        assertThat(volumes.get(0).getName()).isEqualTo("cache-vol");
        assertThat(volumes.get(0).getEmptyDir().getMedium()).isEqualTo("Memory");
        assertThat(volumes.get(0).getEmptyDir().getSizeLimit().toString()).isEqualTo("500Mi");

        // Verify Container volumeMounts
        var containers = dep.getSpec().getTemplate().getSpec().getContainers();
        assertThat(containers).hasSize(1);
        var c = containers.get(0);
        assertThat(c.getVolumeMounts()).hasSize(1);
        assertThat(c.getVolumeMounts().get(0).getName()).isEqualTo("cache-vol");
        assertThat(c.getVolumeMounts().get(0).getMountPath()).isEqualTo("/cache");
        assertThat(c.getVolumeMounts().get(0).getReadOnly()).isFalse();

        // Verify Probes
        assertThat(c.getLivenessProbe()).isNotNull();
        assertThat(c.getLivenessProbe().getHttpGet().getPath()).isEqualTo("/healthz");
        assertThat(c.getLivenessProbe().getHttpGet().getPort().getValue()).isEqualTo(8080);
        assertThat(c.getLivenessProbe().getHttpGet().getHost()).isEqualTo("localhost");
        assertThat(c.getLivenessProbe().getInitialDelaySeconds()).isEqualTo(10);

        assertThat(c.getReadinessProbe()).isNotNull();
        assertThat(c.getReadinessProbe().getExec().getCommand()).containsExactly("cat", "/tmp/ready");

        assertThat(c.getStartupProbe()).isNotNull();
        assertThat(c.getStartupProbe().getTcpSocket().getPort().getValue()).isEqualTo(8080);

        // Verify YAML serialization
        String yaml = Serialization.asYaml(dep);
        assertThat(yaml).contains("cache-vol");
        assertThat(yaml).contains("mountPath: \"/cache\"");
        assertThat(yaml).contains("path: \"/healthz\"");
        assertThat(yaml).contains("medium: \"Memory\"");
    }

    @Test
    public void testStatefulSetWithVolumeClaimTemplates() {
        PersistentVolumeClaimRecord pvc = new PersistentVolumeClaimRecord(
                "data", "fast-storage",
                List.of("ReadWriteOnce"),
                new ResourceRequirementsRecord(null, Map.of("storage", "10Gi"))
        );

        PodSpecRecord podSpec = new PodSpecRecord(
                List.of(new ContainerRecord("app", "image", null, null, null, null, null, null, null, null, null)),
                null, null, null, null, null
        );

        StatefulSetRecord ss = new StatefulSetRecord(
                "stateful-app", 3, "stateful-service",
                Map.of("role", "stateful"),
                Map.of("role", "stateful"),
                null,
                podSpec,
                List.of(pvc)
        );

        HasMetadata result = KubernetesResourceHelper.convert(ss);
        assertThat(result).isInstanceOf(StatefulSet.class);

        StatefulSet statefulSet = (StatefulSet) result;
        assertThat(statefulSet.getSpec().getVolumeClaimTemplates()).hasSize(1);

        PersistentVolumeClaim template = statefulSet.getSpec().getVolumeClaimTemplates().get(0);
        assertThat(template.getMetadata().getName()).isEqualTo("data");
        assertThat(template.getSpec().getStorageClassName()).isEqualTo("fast-storage");
        assertThat(template.getSpec().getAccessModes()).containsExactly("ReadWriteOnce");
        assertThat(template.getSpec().getResources().getRequests().get("storage").toString()).isEqualTo("10Gi");

        String yaml = Serialization.asYaml(statefulSet);
        assertThat(yaml).contains("volumeClaimTemplates:");
        assertThat(yaml).contains("storageClassName: \"fast-storage\"");
        assertThat(yaml).contains("storage: \"10Gi\"");
    }

    @Test
    public void testPersistentVolumeClaimRecordConversion() {
        PersistentVolumeClaimRecord pvcRecord = new PersistentVolumeClaimRecord(
                "my-pvc", "standard",
                Map.of("storage", "20Gi")
        );

        HasMetadata result = KubernetesResourceHelper.convert(pvcRecord);
        assertThat(result).isInstanceOf(PersistentVolumeClaim.class);

        PersistentVolumeClaim pvc = (PersistentVolumeClaim) result;
        assertThat(pvc.getMetadata().getName()).isEqualTo("my-pvc");
        assertThat(pvc.getSpec().getStorageClassName()).isEqualTo("standard");
        assertThat(pvc.getSpec().getAccessModes()).containsExactly("ReadWriteOnce");
        assertThat(pvc.getSpec().getResources().getRequests().get("storage").toString()).isEqualTo("20Gi");
    }

    @Test
    public void testNullSafetyDefensiveGuards() {
        PodSpecRecord minimalPodSpec = new PodSpecRecord(
                List.of(new ContainerRecord("minimal", "image", null, null, null, null, null, null, null, null, null)),
                null, null, null, null, null
        );

        DeploymentRecord deployment = new DeploymentRecord("minimal", 1, null, null, null, minimalPodSpec);
        assertThatCode(() -> {
            HasMetadata result = KubernetesResourceHelper.convert(deployment);
            assertThat(result).isNotNull();
        }).doesNotThrowAnyException();

        ServiceRecord service = new ServiceRecord("minimal-svc", "ClusterIP", null, null, null, null);
        assertThatCode(() -> {
            HasMetadata result = KubernetesResourceHelper.convert(service);
            assertThat(result).isNotNull();
        }).doesNotThrowAnyException();
    }
}
