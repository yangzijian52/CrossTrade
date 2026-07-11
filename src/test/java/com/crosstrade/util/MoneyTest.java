package com.crosstrade.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test void roundsAtConfiguredBoundary() {
        assertEquals(new BigDecimal("10.13"), Money.parse("10.125", 2));
    }

    @Test void rejectsNonPositiveAndNonNumericValues() {
        assertThrows(IllegalArgumentException.class, () -> Money.parse("0", 2));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("-1", 2));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("NaN", 2));
    }

    @Test void validatesConfiguredRange() {
        assertTrue(Money.valid(new BigDecimal("1.00"), new BigDecimal("0.01"), new BigDecimal("10")));
        assertFalse(Money.valid(new BigDecimal("10.01"), new BigDecimal("0.01"), new BigDecimal("10")));
    }
}
