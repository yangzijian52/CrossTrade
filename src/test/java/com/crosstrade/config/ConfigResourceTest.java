package com.crosstrade.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigResourceTest {
    @Test void annotatedDefaultConfigRemainsValidYaml() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertEquals(4, yaml.getInt("config-version"));
        assertEquals(60, yaml.getInt("direct-trade.request-timeout-seconds"));
        assertEquals(10, yaml.getInt("market.default-max-listings"));
        assertEquals("&e双方已确认，交易将在 &6{seconds} &e秒后完成。",
                yaml.getString("messages.trade-countdown"));
    }

    @Test void paperYamlSaveKeepsAdministratorComments() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        Path output = Files.createTempFile("crosstrade-config-comments", ".yml");
        try {
            yaml.save(output.toFile());
            String saved = Files.readString(output, StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertTrue(saved.contains("玩家市场总开关"));
            org.junit.jupiter.api.Assertions.assertTrue(saved.contains("SQLite 数据库"));
        } finally { Files.deleteIfExists(output); }
    }
}
