package com.crosstrade;

import com.crosstrade.command.MarketCommand;
import com.crosstrade.command.TradeAcceptCommand;
import com.crosstrade.command.TradeCommand;
import com.crosstrade.command.TradeDenyCommand;
import com.crosstrade.economy.EconomyGateway;
import com.crosstrade.input.MarketInputManager;
import com.crosstrade.listener.MarketListener;
import com.crosstrade.listener.TradeListener;
import com.crosstrade.manager.TradeManager;
import com.crosstrade.market.gui.MarketGui;
import com.crosstrade.market.gui.GuiConfigManager;
import com.crosstrade.market.repository.MarketRepository;
import com.crosstrade.market.service.MailboxService;
import com.crosstrade.market.service.MarketService;
import com.crosstrade.market.service.PayoutService;
import com.crosstrade.storage.DatabaseManager;
import com.crosstrade.util.AuditLog;
import com.crosstrade.util.BedrockFormUtil;
import com.crosstrade.util.ConfigManager;
import com.crosstrade.util.ItemCodec;
import com.crosstrade.util.ItemNameService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

public final class CrossTrade extends JavaPlugin {
    private static CrossTrade instance;
    private ConfigManager configManager;
    private GuiConfigManager guiConfigManager;
    private EconomyGateway economyGateway;
    private DatabaseManager database;
    private MarketRepository marketRepository;
    private ItemCodec itemCodec;
    private ItemNameService itemNameService;
    private MailboxService mailboxService;
    private PayoutService payoutService;
    private MarketService marketService;
    private MarketGui marketGui;
    private MarketInputManager marketInputManager;
    private TradeManager tradeManager;
    private BedrockFormUtil bedrockFormUtil;
    private AuditLog auditLog;
    private boolean marketAvailable;
    private BukkitTask expirationTask;

    @Override public void onEnable() {
        instance = this;
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        configManager.mergeDefaults();
        guiConfigManager = new GuiConfigManager(this);
        guiConfigManager.load();
        auditLog = new AuditLog(this);
        economyGateway = new EconomyGateway(this);
        if (economyGateway.hook()) getLogger().info("已连接 Vault 经济服务。");
        else getLogger().warning("未找到 Vault 经济服务：市场可浏览，但金钱交易已禁用。");

        itemCodec = new ItemCodec();
        itemNameService = new ItemNameService(this);
        verifyItemCodec();
        database = new DatabaseManager(this);
        try {
            database.open();
            marketRepository = new MarketRepository(database);
            marketRepository.recoverPreparedSales();
            marketAvailable = true;
            getLogger().info("SQLite 市场数据库已打开: " + database.file().getAbsolutePath());
        } catch (Exception exception) {
            marketAvailable = false;
            getLogger().severe("市场数据库启动失败，市场与安全邮箱已禁用: " + exception.getMessage());
            getLogger().log(java.util.logging.Level.SEVERE, "SQLite 初始化异常", exception);
        }

        if (getServer().getPluginManager().isPluginEnabled("floodgate")) {
            bedrockFormUtil = new BedrockFormUtil(this);
            getLogger().info("已连接 Floodgate，基岩版表单已启用。");
        } else getLogger().info("未安装 Floodgate：Java 功能保持可用，基岩表单已跳过加载。");
        tradeManager = new TradeManager(this);

        if (marketRepository != null) {
            mailboxService = new MailboxService(this, marketRepository, itemCodec);
            payoutService = new PayoutService(this, marketRepository, economyGateway);
            marketService = new MarketService(this, marketRepository, itemCodec, economyGateway, mailboxService, payoutService);
            marketGui = new MarketGui(this, marketRepository, itemCodec);
            marketInputManager = new MarketInputManager(this);
        }

        MarketCommand marketCommand = new MarketCommand(this);
        Objects.requireNonNull(getCommand("market")).setExecutor(marketCommand);
        Objects.requireNonNull(getCommand("market")).setTabCompleter(marketCommand);
        Objects.requireNonNull(getCommand("trade")).setExecutor(new TradeCommand(this));
        Objects.requireNonNull(getCommand("tradeaccept")).setExecutor(new TradeAcceptCommand(this));
        Objects.requireNonNull(getCommand("tradedeny")).setExecutor(new TradeDenyCommand(this));
        getServer().getPluginManager().registerEvents(new TradeListener(this), this);
        if (marketAvailable) getServer().getPluginManager().registerEvents(new MarketListener(this), this);

        long scanTicks = Math.max(20L, getConfig().getLong("market.expiration-scan-seconds", 60L) * 20L);
        if (marketAvailable) expirationTask = getServer().getScheduler().runTaskTimer(this, () -> {
                try { marketService.expire(); }
                catch (RuntimeException exception) { getLogger().warning("市场到期扫描失败: " + exception.getMessage()); }
            }, scanTicks, scanTicks);
        getLogger().info("CrossTrade 2.2.0 已启用，Paper 26.2 玩家市场与面对面交易已就绪。");
    }

    @Override public void onDisable() {
        if (expirationTask != null) expirationTask.cancel();
        if (tradeManager != null) tradeManager.closeAllTrades();
        if (auditLog != null) auditLog.close();
        if (database != null) database.close();
        getLogger().info("CrossTrade 已安全关闭。所有数据库写入均已完成。");
    }

    public static CrossTrade getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public GuiConfigManager getGuiConfigManager() { return guiConfigManager; }
    public EconomyGateway getEconomyGateway() { return economyGateway; }
    public Economy getEconomy() { return economyGateway == null ? null : economyGateway.raw(); }
    public boolean hasEconomy() { return economyGateway != null && economyGateway.available(); }
    public DatabaseManager getDatabase() { return database; }
    public MarketRepository getMarketRepository() { return marketRepository; }
    public ItemCodec getItemCodec() { return itemCodec; }
    public ItemNameService getItemNameService() { return itemNameService; }
    public MailboxService getMailboxService() { return mailboxService; }
    public PayoutService getPayoutService() { return payoutService; }
    public MarketService getMarketService() { return marketService; }
    public MarketGui getMarketGui() { return marketGui; }
    public MarketInputManager getMarketInputManager() { return marketInputManager; }
    public TradeManager getTradeManager() { return tradeManager; }
    public BedrockFormUtil getBedrockFormUtil() { return bedrockFormUtil; }
    public boolean isBedrockPlayer(org.bukkit.entity.Player player) {
        return bedrockFormUtil != null && bedrockFormUtil.isBedrockPlayer(player);
    }
    public AuditLog getAuditLog() { return auditLog; }
    public boolean marketAvailable() { return marketAvailable && getConfig().getBoolean("market.enabled", true); }

    private void verifyItemCodec() {
        ItemStack source = new ItemStack(Material.SHULKER_BOX);
        if (!(source.getItemMeta() instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox box))
            throw new IllegalStateException("Paper 未提供潜影盒 BlockStateMeta");
        box.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
        meta.setBlockState(box);
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "codec_self_test"), PersistentDataType.STRING, "ok");
        source.setItemMeta(meta);
        byte[] bytes = itemCodec.encodePrototype(source);
        ItemStack restored = itemCodec.decode(bytes);
        if (!source.isSimilar(restored)
                || !(restored.getItemMeta() instanceof BlockStateMeta restoredMeta)
                || !(restoredMeta.getBlockState() instanceof ShulkerBox restoredBox)
                || restoredBox.getInventory().getItem(0) == null
                || restoredBox.getInventory().getItem(0).getAmount() != 7) {
            throw new IllegalStateException("潜影盒或 PDC ItemStack 序列化自检失败");
        }
        getLogger().info("ItemStack 编解码自检通过（潜影盒内容与 PDC 保持完整）。");
    }
}
