package org.infosmid.stream.kubernetes;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesPropertyResolverTest {

    @Test
    public void testParseStringListCommaSeparated() {
        String input = "rabbit-access,minio-access,neo4j-access,postgresql-access";
        List<String> result = KubernetesPropertyResolver.parseStringList(input);
        assertThat(result).containsExactlyInAnyOrder(
                "rabbit-access",
                "minio-access",
                "neo4j-access",
                "postgresql-access"
        );
    }

    @Test
    public void testParseStringListBracketed() {
        String input = "[rabbit-access, minio-access, neo4j-access, postgresql-access]";
        List<String> result = KubernetesPropertyResolver.parseStringList(input);
        assertThat(result).containsExactlyInAnyOrder(
                "rabbit-access",
                "minio-access",
                "neo4j-access",
                "postgresql-access"
        );
    }

    @Test
    public void testParseStringListBracketedWithQuotes() {
        String input = "['rabbit-access', \"minio-access\"]";
        List<String> result = KubernetesPropertyResolver.parseStringList(input);
        assertThat(result).containsExactlyInAnyOrder("rabbit-access", "minio-access");
    }

    @Test
    public void testParseStringListSingleValue() {
        String input = "rabbit-access";
        List<String> result = KubernetesPropertyResolver.parseStringList(input);
        assertThat(result).containsExactly("rabbit-access");
    }

    @Test
    public void testParseStringListEmptyAndNull() {
        assertThat(KubernetesPropertyResolver.parseStringList(null)).isEmpty();
        assertThat(KubernetesPropertyResolver.parseStringList("")).isEmpty();
        assertThat(KubernetesPropertyResolver.parseStringList("   ")).isEmpty();
        assertThat(KubernetesPropertyResolver.parseStringList("[]")).isEmpty();
    }

    @Test
    public void testParseSecretKeyRefsJsonArray() {
        String input = "[{envVarName: 'SECRET_PASSWORD', secretName: 'mySecret', dataKey: 'password'}," +
                "{envVarName: 'SECRET_USERNAME', secretName: 'mySecret', dataKey: 'username'}]";
        List<KubernetesPropertyResolver.SecretKeyRef> result = KubernetesPropertyResolver.parseSecretKeyRefs(input);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new KubernetesPropertyResolver.SecretKeyRef("SECRET_PASSWORD", "mySecret", "password"));
        assertThat(result.get(1)).isEqualTo(new KubernetesPropertyResolver.SecretKeyRef("SECRET_USERNAME", "mySecret", "username"));
    }

    @Test
    public void testParseSecretKeyRefsSingleJsonObject() {
        String input = "{envVarName: 'DB_PASS', secretName: 'db-secret', dataKey: 'pass'}";
        List<KubernetesPropertyResolver.SecretKeyRef> result = KubernetesPropertyResolver.parseSecretKeyRefs(input);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(new KubernetesPropertyResolver.SecretKeyRef("DB_PASS", "db-secret", "pass"));
    }

    @Test
    public void testParseSecretKeyRefsShorthand() {
        String input = "DB_PASS=db-secret:pass, DB_USER=db-secret:user";
        List<KubernetesPropertyResolver.SecretKeyRef> result = KubernetesPropertyResolver.parseSecretKeyRefs(input);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new KubernetesPropertyResolver.SecretKeyRef("DB_PASS", "db-secret", "pass"));
        assertThat(result.get(1)).isEqualTo(new KubernetesPropertyResolver.SecretKeyRef("DB_USER", "db-secret", "user"));
    }

    @Test
    public void testParseSecretKeyRefsShorthandBracketed() {
        String input = "[DB_PASS=db-secret:pass, DB_USER=db-secret:user]";
        List<KubernetesPropertyResolver.SecretKeyRef> result = KubernetesPropertyResolver.parseSecretKeyRefs(input);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new KubernetesPropertyResolver.SecretKeyRef("DB_PASS", "db-secret", "pass"));
        assertThat(result.get(1)).isEqualTo(new KubernetesPropertyResolver.SecretKeyRef("DB_USER", "db-secret", "user"));
    }

    @Test
    public void testParseConfigMapKeyRefsJson() {
        String input = "[{envVarName: 'CONFIG_URL', configMapName: 'app-config', dataKey: 'endpoint'}]";
        List<KubernetesPropertyResolver.ConfigMapKeyRef> result = KubernetesPropertyResolver.parseConfigMapKeyRefs(input);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(new KubernetesPropertyResolver.ConfigMapKeyRef("CONFIG_URL", "app-config", "endpoint"));
    }

    @Test
    public void testParseConfigMapKeyRefsShorthand() {
        String input = "CONFIG_URL=app-config:endpoint";
        List<KubernetesPropertyResolver.ConfigMapKeyRef> result = KubernetesPropertyResolver.parseConfigMapKeyRefs(input);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(new KubernetesPropertyResolver.ConfigMapKeyRef("CONFIG_URL", "app-config", "endpoint"));
    }

    @Test
    public void testGetSecretRefsFromDeploymentProperties() {
        Map<String, String> props = new java.util.LinkedHashMap<>();
        props.put("spring.cloud.deployer.kubernetes.secret-refs", "sec1,sec2");
        props.put("spring.cloud.deployer.kubernetes.secretRef", "sec3");

        List<String> refs = KubernetesPropertyResolver.getSecretRefs(props);
        assertThat(refs).containsExactlyInAnyOrder("sec1", "sec2", "sec3");
    }

    @Test
    public void testResolvePropertiesAppOverridesDeployerForSecretRefs() {
        Properties metadata = new Properties();
        metadata.setProperty("deployer.*.kubernetes.secret-refs", "default-sec1,default-sec2");

        Properties deployment = new Properties();
        deployment.setProperty("deployer.time.kubernetes.secret-refs", "time-sec");

        Map<String, String> timeProps = KubernetesPropertyResolver.resolveDeploymentProperties(metadata, deployment, "time");
        List<String> timeRefs = KubernetesPropertyResolver.getSecretRefs(timeProps);
        assertThat(timeRefs).containsExactlyInAnyOrder("time-sec", "default-sec1", "default-sec2");

        Map<String, String> logProps = KubernetesPropertyResolver.resolveDeploymentProperties(metadata, deployment, "log");
        List<String> logRefs = KubernetesPropertyResolver.getSecretRefs(logProps);
        assertThat(logRefs).containsExactlyInAnyOrder("default-sec1", "default-sec2");
    }

    @Test
    public void testResolvePropertiesOverrideByVariableForKeyRefs() {
        Properties metadata = new Properties();
        metadata.setProperty("deployer.*.kubernetes.secretKeyRefs", "V1=sec:k1, V2=sec:k2");

        Properties deployment = new Properties();
        deployment.setProperty("deployer.time.kubernetes.secretKeyRefs", "V2=override-sec:k2, V3=sec:k3");

        Map<String, String> timeProps = KubernetesPropertyResolver.resolveDeploymentProperties(metadata, deployment, "time");
        List<KubernetesPropertyResolver.SecretKeyRef> keyRefs = KubernetesPropertyResolver.getSecretKeyRefs(timeProps);

        assertThat(keyRefs).hasSize(3);
        assertThat(keyRefs).containsExactly(
                new KubernetesPropertyResolver.SecretKeyRef("V1", "sec", "k1"),
                new KubernetesPropertyResolver.SecretKeyRef("V2", "override-sec", "k2"),
                new KubernetesPropertyResolver.SecretKeyRef("V3", "sec", "k3")
        );
    }

    @Test
    public void testResolvePropertiesAppOverridesDeployerForConfigMapRefs() {
        Properties metadata = new Properties();
        metadata.setProperty("deployer.*.kubernetes.config-map-refs", "default-cm1,default-cm2");

        Properties deployment = new Properties();
        deployment.setProperty("deployer.time.kubernetes.config-map-refs", "time-cm");

        Map<String, String> timeProps = KubernetesPropertyResolver.resolveDeploymentProperties(metadata, deployment, "time");
        List<String> timeRefs = KubernetesPropertyResolver.getConfigMapRefs(timeProps);
        assertThat(timeRefs).containsExactlyInAnyOrder("time-cm", "default-cm1", "default-cm2");

        Map<String, String> logProps = KubernetesPropertyResolver.resolveDeploymentProperties(metadata, deployment, "log");
        List<String> logRefs = KubernetesPropertyResolver.getConfigMapRefs(logProps);
        assertThat(logRefs).containsExactlyInAnyOrder("default-cm1", "default-cm2");
    }

    @Test
    public void testResolvePropertiesOverrideByVariableForConfigMapKeyRefs() {
        Properties metadata = new Properties();
        metadata.setProperty("deployer.*.kubernetes.configMapKeyRefs", "C1=cm:k1, C2=cm:k2");

        Properties deployment = new Properties();
        deployment.setProperty("deployer.time.kubernetes.configMapKeyRefs", "C2=override-cm:k2, C3=cm:k3");

        Map<String, String> timeProps = KubernetesPropertyResolver.resolveDeploymentProperties(metadata, deployment, "time");
        List<KubernetesPropertyResolver.ConfigMapKeyRef> keyRefs = KubernetesPropertyResolver.getConfigMapKeyRefs(timeProps);

        assertThat(keyRefs).hasSize(3);
        assertThat(keyRefs).containsExactly(
                new KubernetesPropertyResolver.ConfigMapKeyRef("C1", "cm", "k1"),
                new KubernetesPropertyResolver.ConfigMapKeyRef("C2", "override-cm", "k2"),
                new KubernetesPropertyResolver.ConfigMapKeyRef("C3", "cm", "k3")
        );
    }

    @Test
    public void testDeploymentOverridesMetadataPrecedence() {
        Properties metadata = new Properties();
        metadata.setProperty("deployer.*.kubernetes.secretRefs", "meta-sec");

        Properties deployment = new Properties();
        deployment.setProperty("deployer.*.kubernetes.secretRefs", "deploy-sec");

        Map<String, String> props = KubernetesPropertyResolver.resolveDeploymentProperties(metadata, deployment, "time");
        List<String> refs = KubernetesPropertyResolver.getSecretRefs(props);
        assertThat(refs).containsExactlyInAnyOrder("deploy-sec", "meta-sec");
    }
}
