package com.crosstrade.util;

import com.crosstrade.CrossTrade;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuditLog implements AutoCloseable {
    private final CrossTrade plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CrossTrade-Audit");
        thread.setDaemon(true);
        return thread;
    });

    public AuditLog(CrossTrade plugin) { this.plugin = plugin; }

    public void write(String type, String details) {
        if (!plugin.getConfig().getBoolean("logging.transactions", true)) return;
        String relative = plugin.getConfig().getString("logging.file", "logs/transactions.log");
        File file = new File(plugin.getDataFolder(), relative);
        String safe = (details == null ? "" : details).replace('\n', ' ').replace('\r', ' ');
        executor.execute(() -> {
            try {
                if (file.getParentFile() != null) Files.createDirectories(file.getParentFile().toPath());
                Files.writeString(file.toPath(), Instant.now() + "\t" + type + "\t" + safe + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                plugin.getLogger().warning("写入交易审计日志失败: " + exception.getMessage());
            }
        });
    }

    @Override public void close() { executor.shutdown(); }
}
