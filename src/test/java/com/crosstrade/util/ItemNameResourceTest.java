package com.crosstrade.util;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ItemNameResourceTest {
    @Test void paper262ChineseNamesAreCompleteAndUtf8() throws Exception {
        Properties names = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream("item-names-zh_cn.properties")) {
            assertNotNull(stream);
            names.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        assertTrue(names.size() >= 1400);
        assertEquals("钻石", names.getProperty("DIAMOND"));
        assertEquals("潜影盒", names.getProperty("SHULKER_BOX"));
        assertEquals("收纳袋", names.getProperty("BUNDLE"));
    }
}
