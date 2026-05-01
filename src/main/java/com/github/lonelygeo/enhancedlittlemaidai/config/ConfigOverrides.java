package com.github.lonelygeo.enhancedlittlemaidai.config;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从独立的 elmai-overrides.toml 加载运行时的配置覆盖。
 * 该文件不受 NeoForge ModConfigSpec 管理，永远不会被自动重置。
 * <p>
 * 文件格式与 enhancedlittlemaidai-common.toml 一致：
 * <pre>{@code
 * debugLog = true
 * [inter_maid]
 * cooldownTicks = 600
 * scanInterval = 40
 * }</pre>
 */
public final class ConfigOverrides {
    private static final String FILE_NAME = "elmai-overrides.toml";

    private ConfigOverrides() {
    }

    public static void apply() {
        Path overrideFile = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        if (!Files.exists(overrideFile)) return;

        try {
            Map<String, String> entries = parseOverrides(overrideFile);
            if (entries.isEmpty()) return;

            int count = 0;
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                if (applyField(entry.getKey(), entry.getValue())) count++;
            }

            EnhancedLittleMaidAI.LOGGER.info(
                    "EnhancedLittleMaidAI: Applied {} overrides from {}", count, FILE_NAME);
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn(
                    "EnhancedLittleMaidAI: Failed to load {}: {}", FILE_NAME, e.getMessage());
        }
    }

    /**
     * 解析简单 TOML 为 section.key → value 的映射。
     */
    private static Map<String, String> parseOverrides(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        Map<String, String> entries = new LinkedHashMap<>();
        String section = "";

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim() + ".";
                continue;
            }

            int eq = line.indexOf('=');
            if (eq < 0) continue;

            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            entries.put(section + key, value);
        }
        return entries;
    }

    /**
     * 通过反射在 EnhancedConfig 中查找匹配的静态字段并写入值。
     * 字段名匹配规则：将 TOML key（如 cooldownTicks）转为 UPPER_SNAKE_CASE（如 COOLDOWN_TICKS）。
     */
    private static boolean applyField(String compoundKey, String value) {
        // 提取不带 section 前缀的 key
        String fieldName = compoundKey.contains(".")
                ? compoundKey.substring(compoundKey.lastIndexOf('.') + 1)
                : compoundKey;

        // camelCase → UPPER_SNAKE_CASE
        String upperField = camelToUpperSnake(fieldName);

        for (Field field : EnhancedConfig.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!field.getName().equals(upperField)) continue;

            try {
                Object configValue = field.get(null);
                if (configValue instanceof ModConfigSpec.IntValue iv) {
                    iv.set(Integer.parseInt(value));
                } else if (configValue instanceof ModConfigSpec.BooleanValue bv) {
                    bv.set(Boolean.parseBoolean(value));
                } else if (configValue instanceof ModConfigSpec.DoubleValue dv) {
                    dv.set(Double.parseDouble(value));
                } else if (configValue instanceof ModConfigSpec.ConfigValue<?> cv) {
                    try {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        ModConfigSpec.ConfigValue<String> sv = (ModConfigSpec.ConfigValue<String>) cv;
                        sv.set(value);
                    } catch (ClassCastException ignored) {
                        return false;
                    }
                } else {
                    return false;
                }
                return true;
            } catch (Exception e) {
                EnhancedLittleMaidAI.LOGGER.warn(
                        "EnhancedLittleMaidAI: Failed to override {}: {}", compoundKey, e.getMessage());
                return false;
            }
        }
        return false;
    }

    private static String camelToUpperSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(c);
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }
}
