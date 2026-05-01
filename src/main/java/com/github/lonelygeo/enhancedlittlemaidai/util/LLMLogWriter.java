package com.github.lonelygeo.enhancedlittlemaidai.util;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/**
 * 独立 LLM 对话日志，每次会话写入独立文件 logs/llm/LLM-{datetime}.log。
 * 最多保留最近 3 次会话日志，旧文件自动清理。
 * 始终写入，不受 debugLog 控制。
 */
public final class LLMLogWriter {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final Object LOCK = new Object();
    private static Path sessionPath;

    private LLMLogWriter() {
    }

    private static Path getSessionPath() {
        if (sessionPath != null) return sessionPath;
        synchronized (LOCK) {
            if (sessionPath != null) return sessionPath;
            try {
                Path llmDir = FMLPaths.GAMEDIR.get().resolve("logs").resolve("llm");
                Files.createDirectories(llmDir);
                sessionPath = llmDir.resolve("LLM-" + LocalDateTime.now().format(FILE_TS) + ".log");
                Files.createFile(sessionPath);
                cleanupOldLogs(llmDir);
            } catch (Exception e) {
                sessionPath = Path.of("LLM.log");
            }
            return sessionPath;
        }
    }

    private static void cleanupOldLogs(Path dir) {
        try (var stream = Files.list(dir)) {
            var logFiles = stream
                    .filter(f -> f.getFileName().toString().matches("LLM-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.log"))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (int i = 3; i < logFiles.size(); i++) {
                try {
                    Files.delete(logFiles.get(i));
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean shouldWrite() {
        try {
            return ServerLifecycleHooks.getCurrentServer() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static void logRequest(String cbType, String model, String url, int msgCount, String json) {
        if (!shouldWrite()) return;
        Path path = getSessionPath();
        synchronized (LOCK) {
            try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
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
        if (!shouldWrite()) return;
        Path path = getSessionPath();
        synchronized (LOCK) {
            try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
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
