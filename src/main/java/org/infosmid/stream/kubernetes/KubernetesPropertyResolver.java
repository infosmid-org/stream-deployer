/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.infosmid.stream.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.jspecify.annotations.Nullable;

public class KubernetesPropertyResolver {

    private static final Logger LOGGER = Logger.getLogger(KubernetesPropertyResolver.class.getName());

    public record SecretKeyRef(String envVarName, String secretName, String dataKey) {}

    public record ConfigMapKeyRef(String envVarName, String configMapName, String dataKey) {}

    public static List<String> parseStringList(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (String item : trimmed.split(",")) {
            String clean = stripQuotes(item.trim());
            if (!clean.isEmpty()) {
                result.add(clean);
            }
        }
        return result;
    }

    public static List<SecretKeyRef> parseSecretKeyRefs(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = value.trim();

        if (trimmed.startsWith("{") || (trimmed.startsWith("[") && trimmed.contains("{"))) {
            try {
                JsonNode node = Serialization.yamlMapper().readTree(trimmed);
                List<SecretKeyRef> list = new ArrayList<>();
                if (node.isArray()) {
                    for (JsonNode item : node) {
                        SecretKeyRef ref = parseSecretKeyRefNode(item);
                        if (ref != null) {
                            list.add(ref);
                        }
                    }
                } else if (node.isObject()) {
                    SecretKeyRef ref = parseSecretKeyRefNode(node);
                    if (ref != null) {
                        list.add(ref);
                    }
                }
                return list;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cannot parse YAML or JSON secretKeyRefs: " + trimmed, e);
            }
        }

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }

        List<SecretKeyRef> list = new ArrayList<>();
        for (String item : trimmed.split(",")) {
            String clean = stripQuotes(item.trim());
            if (clean.isEmpty()) {
                continue;
            }
            SecretKeyRef ref = parseShorthandSecretKeyRef(clean);
            if (ref != null) {
                list.add(ref);
            }
        }
        return list;
    }

    public static List<ConfigMapKeyRef> parseConfigMapKeyRefs(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = value.trim();

        if (trimmed.startsWith("{") || (trimmed.startsWith("[") && trimmed.contains("{"))) {
            try {
                JsonNode node = Serialization.yamlMapper().readTree(trimmed);
                List<ConfigMapKeyRef> list = new ArrayList<>();
                if (node.isArray()) {
                    for (JsonNode item : node) {
                        ConfigMapKeyRef ref = parseConfigMapKeyRefNode(item);
                        if (ref != null) {
                            list.add(ref);
                        }
                    }
                } else if (node.isObject()) {
                    ConfigMapKeyRef ref = parseConfigMapKeyRefNode(node);
                    if (ref != null) {
                        list.add(ref);
                    }
                }
                return list;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cannot parse YAML or JSON configMapKeyRefs: " + trimmed, e);
            }
        }

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConfigMapKeyRef> list = new ArrayList<>();
        for (String item : trimmed.split(",")) {
            String clean = stripQuotes(item.trim());
            if (clean.isEmpty()) {
                continue;
            }
            ConfigMapKeyRef ref = parseShorthandConfigMapKeyRef(clean);
            if (ref != null) {
                list.add(ref);
            }
        }
        return list;
    }

    public static List<String> getSecretRefs(Map<String, String> properties) {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (key.equals("secretrefs") || key.equals("secretref")) {
                result.addAll(parseStringList(entry.getValue()));
            }
        }
        return new ArrayList<>(result);
    }

    public static List<String> getConfigMapRefs(Map<String, String> properties) {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (key.equals("configmaprefs") || key.equals("configmapref")) {
                result.addAll(parseStringList(entry.getValue()));
            }
        }
        return new ArrayList<>(result);
    }

    public static List<SecretKeyRef> getSecretKeyRefs(Map<String, String> properties) {
        Map<String, SecretKeyRef> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (key.equals("secretkeyrefs") || key.equals("secretkeyref")) {
                List<SecretKeyRef> parsed = parseSecretKeyRefs(entry.getValue());
                for (SecretKeyRef ref : parsed) {
                    result.put(ref.envVarName(), ref);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    public static List<ConfigMapKeyRef> getConfigMapKeyRefs(Map<String, String> properties) {
        Map<String, ConfigMapKeyRef> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (key.equals("configmapkeyrefs") || key.equals("configmapkeyref")) {
                List<ConfigMapKeyRef> parsed = parseConfigMapKeyRefs(entry.getValue());
                for (ConfigMapKeyRef ref : parsed) {
                    result.put(ref.envVarName(), ref);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    public static Map<String, String> resolveDeploymentProperties(
            @Nullable Properties metadataProperties,
            @Nullable Properties deploymentProperties,
            String label
    ) {
        Map<String, String> target = new LinkedHashMap<>();

        // Merge scalar/general properties with precedence
        mergeSourceProperties(metadataProperties, target, "deployer.*.");
        mergeSourceProperties(deploymentProperties, target, "deployer.*.");
        mergeSourceProperties(metadataProperties, target, "deployer." + label + ".");
        mergeSourceProperties(deploymentProperties, target, "deployer." + label + ".");

        // 1. Resolve secretRefs: merge all entries
        List<String> secretRefs = resolveRefs(
                metadataProperties, deploymentProperties, label,
                "secretrefs", "secretref"
        );
        if (!secretRefs.isEmpty()) {
            target.put("spring.cloud.deployer.kubernetes.secret-refs", String.join(",", secretRefs));
        }

        // 2. Resolve configMapRefs: merge all entries
        List<String> configMapRefs = resolveRefs(
                metadataProperties, deploymentProperties, label,
                "configmaprefs", "configmapref"
        );
        if (!configMapRefs.isEmpty()) {
            target.put("spring.cloud.deployer.kubernetes.config-map-refs", String.join(",", configMapRefs));
        }

        // 3. Resolve secretKeyRefs: Override By Variable
        Map<String, SecretKeyRef> deployerSecretKeyRefs = new LinkedHashMap<>();
        collectSecretKeyRefs(metadataProperties, "deployer.*.", deployerSecretKeyRefs);
        collectSecretKeyRefs(deploymentProperties, "deployer.*.", deployerSecretKeyRefs);

        Map<String, SecretKeyRef> appSecretKeyRefs = new LinkedHashMap<>();
        collectSecretKeyRefs(metadataProperties, "deployer." + label + ".", appSecretKeyRefs);
        collectSecretKeyRefs(deploymentProperties, "deployer." + label + ".", appSecretKeyRefs);

        Map<String, SecretKeyRef> mergedSecretKeyRefs = new LinkedHashMap<>(deployerSecretKeyRefs);
        mergedSecretKeyRefs.putAll(appSecretKeyRefs);

        if (!mergedSecretKeyRefs.isEmpty()) {
            try {
                String json = Serialization.jsonMapper().writeValueAsString(mergedSecretKeyRefs.values());
                target.put("spring.cloud.deployer.kubernetes.secret-key-refs", json);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cannot serialize secretKeyRefs to JSON", e);
            }
        }

        // 4. Resolve configMapKeyRefs: Override By Variable
        Map<String, ConfigMapKeyRef> deployerConfigMapKeyRefs = new LinkedHashMap<>();
        collectConfigMapKeyRefs(metadataProperties, "deployer.*.", deployerConfigMapKeyRefs);
        collectConfigMapKeyRefs(deploymentProperties, "deployer.*.", deployerConfigMapKeyRefs);

        Map<String, ConfigMapKeyRef> appConfigMapKeyRefs = new LinkedHashMap<>();
        collectConfigMapKeyRefs(metadataProperties, "deployer." + label + ".", appConfigMapKeyRefs);
        collectConfigMapKeyRefs(deploymentProperties, "deployer." + label + ".", appConfigMapKeyRefs);

        Map<String, ConfigMapKeyRef> mergedConfigMapKeyRefs = new LinkedHashMap<>(deployerConfigMapKeyRefs);
        mergedConfigMapKeyRefs.putAll(appConfigMapKeyRefs);

        if (!mergedConfigMapKeyRefs.isEmpty()) {
            try {
                String json = Serialization.jsonMapper().writeValueAsString(mergedConfigMapKeyRefs.values());
                target.put("spring.cloud.deployer.kubernetes.config-map-key-refs", json);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cannot serialize configMapKeyRefs to JSON", e);
            }
        }

        return target;
    }

    private static List<String> resolveRefs(
            @Nullable Properties metadataProperties,
            @Nullable Properties deploymentProperties,
            String label,
            String... normalizedKeyNames
    ) {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(findRefsWithPrefix(metadataProperties, "deployer.*.", normalizedKeyNames));
        result.addAll(findRefsWithPrefix(deploymentProperties, "deployer.*.", normalizedKeyNames));
        result.addAll(findRefsWithPrefix(metadataProperties, "deployer." + label + ".", normalizedKeyNames));
        result.addAll(findRefsWithPrefix(deploymentProperties, "deployer." + label + ".", normalizedKeyNames));
        return new ArrayList<>(result);
    }

    private static List<String> findRefsWithPrefix(
            @Nullable Properties props,
            String prefix,
            String... normalizedKeyNames
    ) {
        if (props == null) {
            return Collections.emptyList();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String propName = key.substring(prefix.length());
                String normalized = normalizeKey(propName);
                for (String targetKey : normalizedKeyNames) {
                    if (normalized.equals(targetKey)) {
                        result.addAll(parseStringList(props.getProperty(key)));
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static void collectSecretKeyRefs(
            @Nullable Properties props,
            String prefix,
            Map<String, SecretKeyRef> target
    ) {
        if (props == null) {
            return;
        }
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String propName = key.substring(prefix.length());
                String normalized = normalizeKey(propName);
                if (normalized.equals("secretkeyrefs") || normalized.equals("secretkeyref")) {
                    List<SecretKeyRef> refs = parseSecretKeyRefs(props.getProperty(key));
                    for (SecretKeyRef ref : refs) {
                        target.put(ref.envVarName(), ref);
                    }
                }
            }
        }
    }

    private static void collectConfigMapKeyRefs(
            @Nullable Properties props,
            String prefix,
            Map<String, ConfigMapKeyRef> target
    ) {
        if (props == null) {
            return;
        }
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String propName = key.substring(prefix.length());
                String normalized = normalizeKey(propName);
                if (normalized.equals("configmapkeyrefs") || normalized.equals("configmapkeyref")) {
                    List<ConfigMapKeyRef> refs = parseConfigMapKeyRefs(props.getProperty(key));
                    for (ConfigMapKeyRef ref : refs) {
                        target.put(ref.envVarName(), ref);
                    }
                }
            }
        }
    }

    private static void mergeSourceProperties(
            @Nullable Properties source,
            Map<String, String> target,
            String prefix
    ) {
        if (source == null) {
            return;
        }
        for (String key : source.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String propertyName = key.substring(prefix.length());
                String normalized = normalizeKey(propertyName);
                if (isRefProperty(normalized)) {
                    continue;
                }
                String targetKey = propertyName.startsWith("kubernetes.")
                        ? "spring.cloud.deployer." + propertyName
                        : propertyName;
                target.put(targetKey, stripQuotes(source.getProperty(key)));
            }
        }
    }

    private static boolean isRefProperty(String normalized) {
        return normalized.equals("secretrefs") || normalized.equals("secretref")
                || normalized.equals("configmaprefs") || normalized.equals("configmapref")
                || normalized.equals("secretkeyrefs") || normalized.equals("secretkeyref")
                || normalized.equals("configmapkeyrefs") || normalized.equals("configmapkeyref");
    }

    private static String normalizeKey(String key) {
        String k = key;
        if (k.startsWith("spring.cloud.deployer.kubernetes.")) {
            k = k.substring("spring.cloud.deployer.kubernetes.".length());
        } else if (k.startsWith("spring.cloud.deployer.")) {
            k = k.substring("spring.cloud.deployer.".length());
        } else if (k.startsWith("kubernetes.")) {
            k = k.substring("kubernetes.".length());
        }
        return k.replace("-", "").toLowerCase();
    }

    private static @Nullable SecretKeyRef parseSecretKeyRefNode(JsonNode node) {
        String envVarName = getText(node, "envVarName", "env-var-name", "env");
        String secretName = getText(node, "secretName", "secret-name", "name");
        String dataKey = getText(node, "dataKey", "data-key", "key");
        if (envVarName != null && secretName != null && dataKey != null) {
            return new SecretKeyRef(envVarName, secretName, dataKey);
        }
        return null;
    }

    private static @Nullable ConfigMapKeyRef parseConfigMapKeyRefNode(JsonNode node) {
        String envVarName = getText(node, "envVarName", "env-var-name", "env");
        String configMapName = getText(node, "configMapName", "config-map-name", "name");
        String dataKey = getText(node, "dataKey", "data-key", "key");
        if (envVarName != null && configMapName != null && dataKey != null) {
            return new ConfigMapKeyRef(envVarName, configMapName, dataKey);
        }
        return null;
    }

    private static @Nullable SecretKeyRef parseShorthandSecretKeyRef(String item) {
        if (item.contains("=")) {
            String[] parts = item.split("=", 2);
            String envVarName = parts[0].trim();
            String remainder = parts[1].trim();
            String[] secKey = remainder.split(":", 2);
            if (secKey.length == 2) {
                return new SecretKeyRef(envVarName, secKey[0].trim(), secKey[1].trim());
            }
        } else if (item.contains(":")) {
            String[] parts = item.split(":", 3);
            if (parts.length == 3) {
                return new SecretKeyRef(parts[0].trim(), parts[1].trim(), parts[2].trim());
            }
        }
        LOGGER.warning("Invalid shorthand secretKeyRef format: " + item);
        return null;
    }

    private static @Nullable ConfigMapKeyRef parseShorthandConfigMapKeyRef(String item) {
        if (item.contains("=")) {
            String[] parts = item.split("=", 2);
            String envVarName = parts[0].trim();
            String remainder = parts[1].trim();
            String[] cmKey = remainder.split(":", 2);
            if (cmKey.length == 2) {
                return new ConfigMapKeyRef(envVarName, cmKey[0].trim(), cmKey[1].trim());
            }
        } else if (item.contains(":")) {
            String[] parts = item.split(":", 3);
            if (parts.length == 3) {
                return new ConfigMapKeyRef(parts[0].trim(), parts[1].trim(), parts[2].trim());
            }
        }
        LOGGER.warning("Invalid shorthand configMapKeyRef format: " + item);
        return null;
    }

    private static @Nullable String getText(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            if (node.has(name) && !node.get(name).isNull()) {
                return node.get(name).asText();
            }
        }
        return null;
    }

    private static String stripQuotes(String str) {
        if (str == null) {
            return "";
        }
        String s = str.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }
}
