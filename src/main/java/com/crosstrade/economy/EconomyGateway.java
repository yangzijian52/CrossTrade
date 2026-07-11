package com.crosstrade.economy;

import com.crosstrade.CrossTrade;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;

public final class EconomyGateway {
    private final CrossTrade plugin;
    private Economy economy;

    public EconomyGateway(CrossTrade plugin) {
        this.plugin = plugin;
    }

    public boolean hook() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        economy = registration == null ? null : registration.getProvider();
        return economy != null;
    }

    public boolean available() {
        return economy != null;
    }

    public BigDecimal balance(OfflinePlayer player) {
        return BigDecimal.valueOf(available() ? economy.getBalance(player) : 0D);
    }

    public boolean has(OfflinePlayer player, BigDecimal amount) {
        return available() && economy.has(player, amount.doubleValue());
    }

    public Result withdraw(OfflinePlayer player, BigDecimal amount) {
        if (!available()) return Result.fail("Vault 经济服务不可用");
        EconomyResponse response = economy.withdrawPlayer(player, amount.doubleValue());
        return response.transactionSuccess() ? Result.ok() : Result.fail(response.errorMessage);
    }

    public Result deposit(OfflinePlayer player, BigDecimal amount) {
        if (!available()) return Result.fail("Vault 经济服务不可用");
        EconomyResponse response = economy.depositPlayer(player, amount.doubleValue());
        return response.transactionSuccess() ? Result.ok() : Result.fail(response.errorMessage);
    }

    public Economy raw() {
        return economy;
    }

    public record Result(boolean success, String error) {
        public static Result ok() { return new Result(true, ""); }
        public static Result fail(String error) { return new Result(false, error == null ? "未知错误" : error); }
    }
}
