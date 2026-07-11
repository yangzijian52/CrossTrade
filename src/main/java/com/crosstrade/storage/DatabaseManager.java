package com.crosstrade.storage;

import com.crosstrade.CrossTrade;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public final class DatabaseManager implements AutoCloseable {
    private final CrossTrade plugin;
    private final Object lock = new Object();
    private File databaseFile;
    private Connection connection;

    public DatabaseManager(CrossTrade plugin) {
        this.plugin = plugin;
    }

    public void open() throws SQLException, ClassNotFoundException, IOException {
        plugin.getDataFolder().mkdirs();
        databaseFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "data.db"));
        if (databaseFile.isFile() && plugin.getConfig().getBoolean("database.backup-on-start", true)) backup();
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=" + plugin.getConfig().getInt("database.busy-timeout-millis", 3000));
            if (plugin.getConfig().getBoolean("database.wal", true)) statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
        }
        migrate();
        recoverInterruptedStates();
    }

    private void migrate() throws SQLException {
        String[] statements = {
                "CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)",
                "INSERT INTO schema_version(version) SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM schema_version)",
                "CREATE TABLE IF NOT EXISTS listings(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT,seller_uuid TEXT NOT NULL,seller_name TEXT NOT NULL," +
                        "market_name TEXT NOT NULL,item_blob BLOB NOT NULL,item_fingerprint TEXT NOT NULL," +
                        "initial_quantity INTEGER NOT NULL,remaining_quantity INTEGER NOT NULL,unit_price TEXT NOT NULL," +
                        "status TEXT NOT NULL,escrow_state TEXT NOT NULL DEFAULT 'HELD',created_at INTEGER NOT NULL,expires_at INTEGER NOT NULL,version INTEGER NOT NULL DEFAULT 0)",
                "CREATE INDEX IF NOT EXISTS idx_listings_active ON listings(status,expires_at,seller_uuid)",
                "CREATE TABLE IF NOT EXISTS sales(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT,listing_id INTEGER NOT NULL,buyer_uuid TEXT NOT NULL," +
                        "seller_uuid TEXT NOT NULL,market_name TEXT NOT NULL,quantity INTEGER NOT NULL,unit_price TEXT NOT NULL," +
                        "total TEXT NOT NULL,state TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL," +
                        "error TEXT,seller_notified INTEGER NOT NULL DEFAULT 0,buyer_hidden INTEGER NOT NULL DEFAULT 0," +
                        "seller_hidden INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(listing_id) REFERENCES listings(id))",
                "CREATE INDEX IF NOT EXISTS idx_sales_players ON sales(buyer_uuid,seller_uuid,created_at)",
                "CREATE TABLE IF NOT EXISTS mailbox_items(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT,owner_uuid TEXT NOT NULL,listing_id INTEGER,item_blob BLOB NOT NULL," +
                        "quantity INTEGER NOT NULL,reason TEXT NOT NULL,created_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_mailbox_owner ON mailbox_items(owner_uuid,created_at)",
                "CREATE TABLE IF NOT EXISTS payouts(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT,sale_id INTEGER,owner_uuid TEXT NOT NULL,amount TEXT NOT NULL," +
                        "state TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,last_error TEXT," +
                        "FOREIGN KEY(sale_id) REFERENCES sales(id))",
                "CREATE INDEX IF NOT EXISTS idx_payout_owner ON payouts(owner_uuid,state)",
                "CREATE TABLE IF NOT EXISTS refunds(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT,sale_id INTEGER,owner_uuid TEXT NOT NULL,amount TEXT NOT NULL," +
                        "state TEXT NOT NULL,created_at INTEGER NOT NULL,last_error TEXT)",
                "CREATE TABLE IF NOT EXISTS transaction_journal(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT,reference_type TEXT NOT NULL,reference_id INTEGER," +
                        "state TEXT NOT NULL,details TEXT,created_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS player_settings(" +
                        "player_uuid TEXT PRIMARY KEY,sort_mode TEXT NOT NULL DEFAULT 'LATEST',updated_at INTEGER NOT NULL)"
        };
        synchronized (lock) {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) statement.execute(sql);
                if (ensureColumn(connection, "sales", "seller_notified",
                        "ALTER TABLE sales ADD COLUMN seller_notified INTEGER NOT NULL DEFAULT 0")) {
                    // 升级前已经完成的交易不重复补发历史通知。
                    statement.executeUpdate("UPDATE sales SET seller_notified=1");
                }
                if (ensureColumn(connection, "listings", "escrow_state",
                        "ALTER TABLE listings ADD COLUMN escrow_state TEXT NOT NULL DEFAULT 'HELD'")) {
                    // 2.0.1 及更早版本在下架、到期和售罄时已经返还或发完物品，标记后可防止重复返还。
                    statement.executeUpdate("UPDATE listings SET escrow_state='RETURNED' WHERE status IN ('CANCELLED','EXPIRED','ADMIN_REMOVED','SOLD_OUT')");
                }
                ensureColumn(connection, "sales", "buyer_hidden",
                        "ALTER TABLE sales ADD COLUMN buyer_hidden INTEGER NOT NULL DEFAULT 0");
                ensureColumn(connection, "sales", "seller_hidden",
                        "ALTER TABLE sales ADD COLUMN seller_hidden INTEGER NOT NULL DEFAULT 0");
                statement.executeUpdate("UPDATE schema_version SET version=3 WHERE version<3");
            }
        }
    }

    private static boolean ensureColumn(Connection connection, String table, String column, String alterSql) throws SQLException {
        try (Statement statement = connection.createStatement();
             java.sql.ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) if (column.equalsIgnoreCase(result.getString("name"))) return false;
        }
        try (Statement statement = connection.createStatement()) { statement.executeUpdate(alterSql); }
        return true;
    }

    private void recoverInterruptedStates() throws SQLException {
        synchronized (lock) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE payouts SET state='REVIEW',last_error='服务器在付款过程中退出',updated_at=strftime('%s','now')*1000 WHERE state='PAYING'");
                statement.executeUpdate("UPDATE sales SET state='REVIEW',error='服务器在资金操作过程中退出',updated_at=strftime('%s','now')*1000 WHERE state IN ('WITHDRAWING','DEBITED')");
                statement.executeUpdate("UPDATE listings SET status='REVIEW',version=version+1 WHERE status='DRAFT'");
            }
        }
    }

    public <T> T use(SqlFunction<Connection, T> operation) {
        synchronized (lock) {
            try {
                if (connection == null || connection.isClosed()) throw new SQLException("数据库连接未打开");
                return operation.apply(connection);
            } catch (SQLException exception) {
                throw new DatabaseException(exception);
            }
        }
    }

    public <T> T transaction(SqlFunction<Connection, T> operation) {
        synchronized (lock) {
            try {
                boolean oldAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    T result = operation.apply(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(oldAutoCommit);
                }
            } catch (SQLException exception) {
                throw new DatabaseException(exception);
            }
        }
    }

    public File backup() throws IOException {
        if (databaseFile == null) databaseFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "data.db"));
        File directory = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.backup-directory", "backups"));
        Files.createDirectories(directory.toPath());
        File target = new File(directory, "data-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".db");
        Files.deleteIfExists(target.toPath());
        synchronized (lock) {
            if (connection != null) {
                try (Statement statement = connection.createStatement()) {
                    String escaped = target.getAbsolutePath().replace("'", "''");
                    statement.execute("VACUUM INTO '" + escaped + "'");
                } catch (SQLException exception) {
                    throw new IOException("SQLite 在线备份失败", exception);
                }
            } else {
                Files.copy(databaseFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        return target;
    }

    public File file() { return databaseFile; }

    @Override
    public void close() {
        synchronized (lock) {
            if (connection != null) {
                try { connection.close(); } catch (SQLException exception) {
                    plugin.getLogger().warning("关闭 SQLite 失败: " + exception.getMessage());
                }
            }
        }
    }

    @FunctionalInterface
    public interface SqlFunction<C, T> { T apply(C value) throws SQLException; }

    public static final class DatabaseException extends RuntimeException {
        public DatabaseException(Throwable cause) { super(cause); }
    }
}
