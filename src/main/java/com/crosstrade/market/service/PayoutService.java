package com.crosstrade.market.service;

import com.crosstrade.CrossTrade;
import com.crosstrade.economy.EconomyGateway;
import com.crosstrade.market.model.Payout;
import com.crosstrade.market.repository.MarketRepository;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;

public final class PayoutService {
    private final CrossTrade plugin;
    private final MarketRepository repository;
    private final EconomyGateway economy;

    public PayoutService(CrossTrade plugin, MarketRepository repository, EconomyGateway economy) {
        this.plugin = plugin;
        this.repository = repository;
        this.economy = economy;
    }

    public BigDecimal pending(java.util.UUID owner) {
        return repository.payouts(owner, "PENDING").stream().map(Payout::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal claim(Player player) {
        if (!economy.available()) return BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        List<Payout> payouts = repository.payouts(player.getUniqueId(), "PENDING");
        for (Payout payout : payouts) {
            if (!repository.beginPayout(payout.id(), player.getUniqueId())) continue;
            EconomyGateway.Result result = economy.deposit(player, payout.amount());
            repository.finishPayout(payout.id(), result.success(), false, result.error());
            if (result.success()) {
                paid = paid.add(payout.amount());
                plugin.getAuditLog().write("PAYOUT_PAID", "payout=" + payout.id() + " owner=" + player.getUniqueId() + " amount=" + payout.amount());
            } else {
                plugin.getAuditLog().write("PAYOUT_FAILED", "payout=" + payout.id() + " error=" + result.error());
                break;
            }
        }
        return paid;
    }

    public void attemptOnline(java.util.UUID owner) {
        if (!plugin.getConfig().getBoolean("market.auto-pay-online-sellers", true)) return;
        Player player = plugin.getServer().getPlayer(owner);
        if (player != null && player.isOnline()) claim(player);
    }
}
