package com.crosstrade.market.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GuiConfigResourceTest {
    @Test void everyInventoryPageHasIndependentConfiguration() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("gui.yml");
        assertNotNull(stream);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        List<String> pages = List.of("home", "player-trade", "plaza", "seller", "buy-quantity", "buy-confirm",
                "sell-select", "sell-quantity", "sell-duration", "sell-confirm", "mine", "mine-clear", "manage",
                "listing-cancel", "listing-delete", "restock", "relist-duration", "mailbox", "earnings", "history",
                "history-clear", "container", "direct");
        for (String page : pages) {
            assertNotNull(yaml.getString("pages." + page + ".title"), page + " 缺少标题");
            assertNotNull(yaml.getString("pages." + page + ".filler.material"), page + " 缺少填充材质");
            assertNotNull(yaml.getConfigurationSection("pages." + page + ".buttons"), page + " 缺少按钮配置");
        }
    }
}
