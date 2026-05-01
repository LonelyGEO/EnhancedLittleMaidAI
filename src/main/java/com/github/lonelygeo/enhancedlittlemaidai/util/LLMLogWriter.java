package com.github.lonelygeo.enhancedlittlemaidai.util;

import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 独立 LLM 对话日志，直接写文件 logs/LLM.log，与 log4j 解耦。
 * 始终写入，不受 debugLog 控制。
 */
public final class LLMLogWriter {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Object LOCK = new Object();
    private static Path path;

    private LLMLogWriter() {
    }

    private static Path getPath() {
        if (path == null) {
            try {
                Path logsDir = FMLPaths.GAMEDIR.get().resolve("logs");
                Files.createDirectories(logsDir);
                path = logsDir.resolve("LLM.log");
            } catch (IOException e) {
                path = Path.of("LLM.log");
            }
        }
        return path;
    }

    public static void logRequest(String cbType, String model, String url, int msgCount, String json) {
        synchronized (LOCK) {
            try (BufferedWriter w = Files.newBufferedWriter(getPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write("==== LLM REQUEST [");
                w.write(LocalDateTime.now().format(TS));
                w.write("] ====");
                w.newLine();
                w.write("Callback: ");
                w.write(cbType);
                w.newLine();
                w.write("Model: ");
                w.write(model);
                w.newLine();
                w.write("URL: ");
                w.write(url);
                w.newLine();
                w.write("Messages: ");
                w.write(String.valueOf(msgCount));
                w.newLine();
                w.write("Body:");
                w.newLine();
                w.write(json);
                w.newLine();
                w.newLine();
            } catch (IOException ignored) {
            }
        }
    }

    public static void logResponse(String role, String content, String reasoningContent) {
        synchronized (LOCK) {
            try (BufferedWriter w = Files.newBufferedWriter(getPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write("---- LLM RESPONSE [");
                w.write(LocalDateTime.now().format(TS));
                w.write("] ----");
                w.newLine();
                w.write("Role: ");
                w.write(role);
                w.newLine();
                if (reasoningContent != null && !reasoningContent.isEmpty()) {
                    w.write("Reasoning: ");
                    w.write(reasoningContent);
                    w.newLine();
                }
                w.write("Content: ");
                w.write(content != null ? content : "(empty)");
                w.newLine();
                w.newLine();
            } catch (IOException ignored) {
            }
        }
    }
}
