package com.crosstrade.market.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketStatusTest {
    @Test void translatesPlayerVisibleStatesToChinese() {
        assertEquals("在售", MarketStatus.listing("ACTIVE"));
        assertEquals("已下架", MarketStatus.listing("CANCELLED"));
        assertEquals("已售罄", MarketStatus.listing("SOLD_OUT"));
        assertEquals("已完成", MarketStatus.sale("COMPLETE"));
        assertEquals("待人工复核", MarketStatus.sale("REVIEW"));
        assertEquals("商品到期返还", MarketStatus.reason("EXPIRED"));
    }
}
