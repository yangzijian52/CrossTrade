package com.crosstrade.market.model;

public final class MarketStatus {
    private MarketStatus() {}

    public static String listing(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "ACTIVE" -> "在售";
            case "CANCELLED" -> "已下架";
            case "SOLD_OUT" -> "已售罄";
            case "EXPIRED" -> "已到期";
            case "ADMIN_REMOVED" -> "管理员下架";
            case "DRAFT" -> "待上架";
            case "REVIEW" -> "待人工复核";
            case "DELETED" -> "已删除";
            default -> "未知状态";
        };
    }

    public static String sale(String state) {
        if (state == null) return "未知";
        return switch (state) {
            case "PREPARED" -> "已预留";
            case "WITHDRAWING" -> "扣款中";
            case "DEBITED" -> "已扣款";
            case "COMPLETE" -> "已完成";
            case "CANCELLED" -> "已取消";
            case "REVIEW" -> "待人工复核";
            default -> "处理中";
        };
    }

    public static String reason(String reason) {
        if (reason == null) return "其他返还";
        return switch (reason) {
            case "EXPIRED" -> "商品到期返还";
            case "SELLER_CANCELLED" -> "卖家下架返还";
            case "ADMIN_REMOVED" -> "管理员下架返还";
            case "LISTING_DELETED" -> "删除商品返还";
            case "DIRECT_TRADE_RECEIVE" -> "面对面交易收取";
            case "PLAYER_CANCELLED" -> "面对面交易取消";
            case "RESTOCK_FAILED" -> "补货失败返还";
            default -> "安全返还";
        };
    }
}
