package com.crosstrade.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    public static BigDecimal parse(String input, int scale) {
        if (input == null) throw new IllegalArgumentException("金额为空");
        BigDecimal value = new BigDecimal(input.trim());
        if (value.signum() <= 0) throw new IllegalArgumentException("金额必须大于零");
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal normalize(BigDecimal value, int scale) {
        if (value == null) return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    public static boolean valid(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value != null && value.signum() > 0
                && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }
}
