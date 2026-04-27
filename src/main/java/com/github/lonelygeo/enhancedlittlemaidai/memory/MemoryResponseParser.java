package com.github.lonelygeo.enhancedlittlemaidai.memory;

import net.minecraft.core.BlockPos;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * 解析 LLM 记忆提取返回文本。
 * 格式：[CATEGORY] content | location:x,y,z,dimension (optional) | importance:N
 */
public final class MemoryResponseParser {
    private MemoryResponseParser() {}

    public static List<MemoryItem> parse(String llmOutput) {
        List<MemoryItem> items = new ArrayList<>();
        if (StringUtils.isBlank(llmOutput)) return items;

        for (String line : llmOutput.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            MemoryCategory category = extractCategory(line);
            if (category == null) continue;

            String content = extractContent(line);
            if (content.isEmpty() || content.length() > 80) continue;

            Optional<BlockPos> location = extractLocation(line);
            Optional<String> dimension = extractDimension(line);
            int importance = extractImportance(line);

            items.add(new MemoryItem(
                    UUID.randomUUID(),
                    category,
                    content,
                    location,
                    dimension,
                    System.currentTimeMillis() / 50L,
                    0,
                    importance
            ));
        }
        return items;
    }

    private static MemoryCategory extractCategory(String line) {
        for (MemoryCategory cat : MemoryCategory.values()) {
            if (line.contains("[" + cat.name() + "]")) return cat;
        }
        return null;
    }

    private static String extractContent(String line) {
        int start = line.indexOf(']');
        if (start < 0) return "";
        int end = line.indexOf('|', start);
        if (end < 0) end = line.length();
        return line.substring(start + 1, end).trim();
    }

    private static Optional<BlockPos> extractLocation(String line) {
        int start = line.indexOf("location:");
        if (start < 0) return Optional.empty();
        String locPart = line.substring(start + 9).split("[\\s|]+")[0];
        String[] parts = locPart.split(",");
        if (parts.length < 3) return Optional.empty();
        try {
            return Optional.of(new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            ));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> extractDimension(String line) {
        int start = line.indexOf("location:");
        if (start < 0) return Optional.empty();
        String locPart = line.substring(start + 9).split("[\\s|]+")[0];
        String[] parts = locPart.split(",");
        if (parts.length >= 4) return Optional.of(parts[3].trim());
        return Optional.empty();
    }

    private static int extractImportance(String line) {
        int start = line.indexOf("importance:");
        if (start < 0) return 3;
        try {
            return Integer.parseInt(line.substring(start + 11).trim().split("[\\s|]+")[0]);
        } catch (NumberFormatException e) {
            return 3;
        }
    }
}
