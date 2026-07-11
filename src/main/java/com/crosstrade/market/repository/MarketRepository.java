package com.crosstrade.market.repository;

import com.crosstrade.market.model.Listing;
import com.crosstrade.market.model.MailboxEntry;
import com.crosstrade.market.model.Payout;
import com.crosstrade.market.model.SaleRecord;
import com.crosstrade.market.model.SellerSummary;
import com.crosstrade.storage.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MarketRepository {
    private final DatabaseManager database;

    public MarketRepository(DatabaseManager database) {
        this.database = database;
    }

    public long createDraft(UUID seller, String sellerName, String marketName, byte[] itemBytes, String fingerprint,
                            int quantity, BigDecimal unitPrice, long createdAt, long expiresAt) {
        return database.use(connection -> {
            String sql = "INSERT INTO listings(seller_uuid,seller_name,market_name,item_blob,item_fingerprint," +
                    "initial_quantity,remaining_quantity,unit_price,status,created_at,expires_at,version) VALUES(?,?,?,?,?,?,?,?,?,?,?,0)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, seller.toString());
                statement.setString(2, sellerName);
                statement.setString(3, marketName);
                statement.setBytes(4, itemBytes);
                statement.setString(5, fingerprint);
                statement.setInt(6, quantity);
                statement.setInt(7, quantity);
                statement.setString(8, unitPrice.toPlainString());
                statement.setString(9, "DRAFT");
                statement.setLong(10, createdAt);
                statement.setLong(11, expiresAt);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("未获得商品编号");
                    return keys.getLong(1);
                }
            }
        });
    }

    public boolean activate(long listingId) {
        return update("UPDATE listings SET status='ACTIVE',version=version+1 WHERE id=? AND status='DRAFT'", listingId) == 1;
    }

    public void deleteDraft(long listingId) {
        update("DELETE FROM listings WHERE id=? AND status='DRAFT'", listingId);
    }

    public Optional<Listing> find(long listingId) {
        return database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM listings WHERE id=?")) {
                statement.setLong(1, listingId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readListing(result)) : Optional.empty();
                }
            }
        });
    }

    public List<SellerSummary> sellers(String search, String sort, int limit, int offset, long now) {
        return database.use(connection -> {
            String order = switch (sort == null ? "LATEST" : sort.toUpperCase()) {
                case "NAME" -> "seller_name COLLATE NOCASE ASC";
                case "COUNT" -> "listing_count DESC,latest_listing DESC";
                default -> "latest_listing DESC";
            };
            String sql = "SELECT seller_uuid,MAX(seller_name) seller_name,COUNT(*) listing_count," +
                    "SUM(remaining_quantity) item_count,MIN(CAST(unit_price AS REAL)) minimum_price," +
                    "MAX(created_at) latest_listing FROM listings WHERE status='ACTIVE' AND expires_at>? " +
                    "AND (?='' OR seller_name LIKE ?) GROUP BY seller_uuid ORDER BY " + order + " LIMIT ? OFFSET ?";
            List<SellerSummary> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, now);
                statement.setString(2, search == null ? "" : search);
                statement.setString(3, "%" + (search == null ? "" : search) + "%");
                statement.setInt(4, limit);
                statement.setInt(5, offset);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) rows.add(new SellerSummary(
                            UUID.fromString(result.getString("seller_uuid")), result.getString("seller_name"),
                            result.getInt("listing_count"), result.getInt("item_count"),
                            BigDecimal.valueOf(result.getDouble("minimum_price")), result.getLong("latest_listing")));
                }
            }
            return rows;
        });
    }

    public int sellerCount(String search, long now) {
        return database.use(connection -> scalarInt(connection,
                "SELECT COUNT(DISTINCT seller_uuid) FROM listings WHERE status='ACTIVE' AND expires_at>? AND (?='' OR seller_name LIKE ?)",
                now, search == null ? "" : search, "%" + (search == null ? "" : search) + "%"));
    }

    public List<Listing> activeBySeller(UUID seller, String search, String sort, int limit, int offset, long now) {
        String order = listingOrder(sort);
        return database.use(connection -> listings(connection,
                "SELECT * FROM listings WHERE seller_uuid=? AND status='ACTIVE' AND expires_at>? " +
                        "AND (?='' OR market_name LIKE ?) ORDER BY " + order + " LIMIT ? OFFSET ?",
                seller.toString(), now, search == null ? "" : search,
                "%" + (search == null ? "" : search) + "%", limit, offset));
    }

    public int activeCountBySeller(UUID seller, String search, long now) {
        return database.use(connection -> scalarInt(connection,
                "SELECT COUNT(*) FROM listings WHERE seller_uuid=? AND status='ACTIVE' AND expires_at>? " +
                        "AND (?='' OR market_name LIKE ?)", seller.toString(), now, search == null ? "" : search,
                "%" + (search == null ? "" : search) + "%"));
    }

    public List<Listing> mine(UUID owner, int limit, int offset) {
        return database.use(connection -> listings(connection,
                "SELECT * FROM listings WHERE seller_uuid=? AND status<>'DELETED' ORDER BY created_at DESC LIMIT ? OFFSET ?",
                owner.toString(), limit, offset));
    }

    public int mineCount(UUID owner) {
        return database.use(connection -> scalarInt(connection,
                "SELECT COUNT(*) FROM listings WHERE seller_uuid=? AND status<>'DELETED'", owner.toString()));
    }

    public int activeCount(UUID owner, long now) {
        return database.use(connection -> scalarInt(connection,
                "SELECT COUNT(*) FROM listings WHERE seller_uuid=? AND status='ACTIVE' AND expires_at>?", owner.toString(), now));
    }

    public Reservation reserve(long listingId, int quantity, int expectedVersion, UUID buyer, long now) {
        return database.transaction(connection -> {
            Listing listing;
            try (PreparedStatement select = connection.prepareStatement("SELECT * FROM listings WHERE id=?")) {
                select.setLong(1, listingId);
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next()) return null;
                    listing = readListing(result);
                }
            }
            if (!listing.active(now) || listing.version() != expectedVersion || listing.remainingQuantity() < quantity) return null;
            int remaining = listing.remainingQuantity() - quantity;
            String status = remaining == 0 ? "SOLD_OUT" : "ACTIVE";
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE listings SET remaining_quantity=?,status=?,escrow_state=?,version=version+1 " +
                            "WHERE id=? AND version=? AND status='ACTIVE' AND escrow_state='HELD'")) {
                update.setInt(1, remaining);
                update.setString(2, status);
                update.setString(3, remaining == 0 ? "RETURNED" : "HELD");
                update.setLong(4, listingId);
                update.setInt(5, expectedVersion);
                if (update.executeUpdate() != 1) return null;
            }
            BigDecimal total = listing.unitPrice().multiply(BigDecimal.valueOf(quantity));
            long saleId;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO sales(listing_id,buyer_uuid,seller_uuid,market_name,quantity,unit_price,total,state,created_at,updated_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, listingId);
                insert.setString(2, buyer.toString());
                insert.setString(3, listing.sellerId().toString());
                insert.setString(4, listing.marketName());
                insert.setInt(5, quantity);
                insert.setString(6, listing.unitPrice().toPlainString());
                insert.setString(7, total.toPlainString());
                insert.setString(8, "PREPARED");
                insert.setLong(9, now);
                insert.setLong(10, now);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("未获得交易编号");
                    saleId = keys.getLong(1);
                }
            }
            journal(connection, "SALE", saleId, "PREPARED", "listing=" + listingId + ",quantity=" + quantity, now);
            return new Reservation(saleId, listing, quantity, total);
        });
    }

    public boolean changeSaleState(long saleId, String expected, String next, String error) {
        return database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE sales SET state=?,error=?,updated_at=? WHERE id=? AND state=?")) {
                statement.setString(1, next);
                statement.setString(2, error);
                statement.setLong(3, System.currentTimeMillis());
                statement.setLong(4, saleId);
                statement.setString(5, expected);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public void completeSale(Reservation reservation, BigDecimal sellerAmount, long now) {
        database.transaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE sales SET state='COMPLETE',updated_at=? WHERE id=? AND state='DEBITED'")) {
                update.setLong(1, now);
                update.setLong(2, reservation.saleId());
                if (update.executeUpdate() != 1) throw new SQLException("交易状态不是 DEBITED");
            }
            try (PreparedStatement payout = connection.prepareStatement(
                    "INSERT INTO payouts(sale_id,owner_uuid,amount,state,created_at,updated_at) VALUES(?,?,?,?,?,?)")) {
                payout.setLong(1, reservation.saleId());
                payout.setString(2, reservation.listing().sellerId().toString());
                payout.setString(3, sellerAmount.toPlainString());
                payout.setString(4, "PENDING");
                payout.setLong(5, now);
                payout.setLong(6, now);
                payout.executeUpdate();
            }
            journal(connection, "SALE", reservation.saleId(), "COMPLETE", "seller_amount=" + sellerAmount, now);
            return null;
        });
    }

    public void rollbackReservation(Reservation reservation, String error) {
        database.transaction(connection -> {
            String state;
            try (PreparedStatement select = connection.prepareStatement("SELECT state FROM sales WHERE id=?")) {
                select.setLong(1, reservation.saleId());
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next()) return null;
                    state = result.getString(1);
                }
            }
            if (!("PREPARED".equals(state) || "WITHDRAWING".equals(state))) return null;
            try (PreparedStatement updateListing = connection.prepareStatement(
                    "UPDATE listings SET remaining_quantity=remaining_quantity+?,status='ACTIVE',escrow_state='HELD',version=version+1 WHERE id=?")) {
                updateListing.setInt(1, reservation.quantity());
                updateListing.setLong(2, reservation.listing().id());
                updateListing.executeUpdate();
            }
            try (PreparedStatement updateSale = connection.prepareStatement(
                    "UPDATE sales SET state='CANCELLED',error=?,updated_at=? WHERE id=?")) {
                updateSale.setString(1, error);
                updateSale.setLong(2, System.currentTimeMillis());
                updateSale.setLong(3, reservation.saleId());
                updateSale.executeUpdate();
            }
            return null;
        });
    }

    public void markSaleReview(long saleId, String error) {
        database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE sales SET state='REVIEW',error=?,updated_at=? WHERE id=?")) {
                statement.setString(1, error);
                statement.setLong(2, System.currentTimeMillis());
                statement.setLong(3, saleId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void rollbackDebited(Reservation reservation, String error) {
        database.transaction(connection -> {
            try (PreparedStatement listing = connection.prepareStatement(
                    "UPDATE listings SET remaining_quantity=remaining_quantity+?,status='ACTIVE',escrow_state='HELD',version=version+1 WHERE id=?")) {
                listing.setInt(1, reservation.quantity());
                listing.setLong(2, reservation.listing().id());
                listing.executeUpdate();
            }
            try (PreparedStatement sale = connection.prepareStatement(
                    "UPDATE sales SET state='CANCELLED',error=?,updated_at=? WHERE id=? AND state='DEBITED'")) {
                sale.setString(1, error);
                sale.setLong(2, System.currentTimeMillis());
                sale.setLong(3, reservation.saleId());
                if (sale.executeUpdate() != 1) throw new SQLException("无法回滚已扣款交易");
            }
            return null;
        });
    }

    public void addRefund(long saleId, UUID owner, BigDecimal amount, String error) {
        database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO refunds(sale_id,owner_uuid,amount,state,created_at,last_error) VALUES(?,?,?,?,?,?)")) {
                statement.setLong(1, saleId);
                statement.setString(2, owner.toString());
                statement.setString(3, amount.toPlainString());
                statement.setString(4, "PENDING");
                statement.setLong(5, System.currentTimeMillis());
                statement.setString(6, error);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void addMailbox(UUID owner, Long listingId, byte[] itemBytes, int quantity, String reason) {
        if (quantity <= 0) return;
        database.use(connection -> {
            addMailbox(connection, owner, listingId, itemBytes, quantity, reason, System.currentTimeMillis());
            return null;
        });
    }

    public List<MailboxEntry> mailbox(UUID owner, int limit, int offset) {
        return database.use(connection -> {
            List<MailboxEntry> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM mailbox_items WHERE owner_uuid=? ORDER BY created_at ASC LIMIT ? OFFSET ?")) {
                statement.setString(1, owner.toString());
                statement.setInt(2, limit);
                statement.setInt(3, offset);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) rows.add(new MailboxEntry(result.getLong("id"), owner,
                            nullableLong(result, "listing_id"), result.getBytes("item_blob"), result.getInt("quantity"),
                            result.getString("reason"), result.getLong("created_at")));
                }
            }
            return rows;
        });
    }

    public int mailboxCount(UUID owner) {
        return database.use(connection -> scalarInt(connection, "SELECT COUNT(*) FROM mailbox_items WHERE owner_uuid=?", owner.toString()));
    }

    public boolean updateMailboxQuantity(long id, UUID owner, int quantity) {
        if (quantity <= 0) return update("DELETE FROM mailbox_items WHERE id=? AND owner_uuid=?", id, owner.toString()) == 1;
        return database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE mailbox_items SET quantity=? WHERE id=? AND owner_uuid=?")) {
                statement.setInt(1, quantity);
                statement.setLong(2, id);
                statement.setString(3, owner.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public List<Payout> payouts(UUID owner, String state) {
        return database.use(connection -> {
            List<Payout> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM payouts WHERE owner_uuid=? AND (?='' OR state=?) ORDER BY created_at ASC")) {
                statement.setString(1, owner.toString());
                statement.setString(2, state == null ? "" : state);
                statement.setString(3, state == null ? "" : state);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) rows.add(new Payout(result.getLong("id"), nullableLong(result, "sale_id"), owner,
                            new BigDecimal(result.getString("amount")), result.getString("state"),
                            result.getLong("created_at"), result.getString("last_error")));
                }
            }
            return rows;
        });
    }

    public boolean beginPayout(long id, UUID owner) {
        return database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE payouts SET state='PAYING',updated_at=? WHERE id=? AND owner_uuid=? AND state='PENDING'")) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setLong(2, id);
                statement.setString(3, owner.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public void finishPayout(long id, boolean success, boolean review, String error) {
        database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE payouts SET state=?,updated_at=?,last_error=? WHERE id=? AND state='PAYING'")) {
                statement.setString(1, success ? "PAID" : (review ? "REVIEW" : "PENDING"));
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, error);
                statement.setLong(4, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void addPayout(UUID owner, BigDecimal amount, String note) {
        database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO payouts(sale_id,owner_uuid,amount,state,created_at,updated_at,last_error) VALUES(NULL,?,?,?,?,?,?)")) {
                statement.setString(1, owner.toString());
                statement.setString(2, amount.toPlainString());
                statement.setString(3, "PENDING");
                statement.setLong(4, System.currentTimeMillis());
                statement.setLong(5, System.currentTimeMillis());
                statement.setString(6, note);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public List<SaleRecord> history(UUID player, int limit, int offset) {
        return database.use(connection -> {
            List<SaleRecord> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM sales WHERE (buyer_uuid=? AND buyer_hidden=0) OR (seller_uuid=? AND seller_hidden=0) " +
                            "ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
                statement.setString(1, player.toString());
                statement.setString(2, player.toString());
                statement.setInt(3, limit);
                statement.setInt(4, offset);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) rows.add(readSale(result));
                }
            }
            return rows;
        });
    }

    public int historyCount(UUID player) {
        return database.use(connection -> scalarInt(connection,
                "SELECT COUNT(*) FROM sales WHERE (buyer_uuid=? AND buyer_hidden=0) OR (seller_uuid=? AND seller_hidden=0)",
                player.toString(), player.toString()));
    }

    public int clearHistory(UUID player) {
        return database.transaction(connection -> {
            int changed;
            try (PreparedStatement buyer = connection.prepareStatement(
                    "UPDATE sales SET buyer_hidden=1 WHERE buyer_uuid=? AND buyer_hidden=0")) {
                buyer.setString(1, player.toString());
                changed = buyer.executeUpdate();
            }
            try (PreparedStatement seller = connection.prepareStatement(
                    "UPDATE sales SET seller_hidden=1 WHERE seller_uuid=? AND seller_hidden=0")) {
                seller.setString(1, player.toString());
                changed += seller.executeUpdate();
            }
            return changed;
        });
    }

    public List<SaleRecord> unnotifiedSales(UUID seller, int limit) {
        return database.use(connection -> {
            List<SaleRecord> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM sales WHERE seller_uuid=? AND state='COMPLETE' AND seller_notified=0 ORDER BY created_at ASC LIMIT ?")) {
                statement.setString(1, seller.toString());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) { while (result.next()) rows.add(readSale(result)); }
            }
            return rows;
        });
    }

    public void markSellerNotified(long saleId) {
        update("UPDATE sales SET seller_notified=1 WHERE id=?", saleId);
    }

    public List<SaleRecord> reviewSales() {
        return database.use(connection -> {
            List<SaleRecord> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM sales WHERE state IN ('REVIEW','WITHDRAWING','DEBITED') ORDER BY created_at ASC")) {
                try (ResultSet result = statement.executeQuery()) { while (result.next()) rows.add(readSale(result)); }
            }
            return rows;
        });
    }

    public List<Listing> reviewListings() {
        return database.use(connection -> listings(connection,
                "SELECT * FROM listings WHERE status='REVIEW' ORDER BY created_at ASC"));
    }

    public List<Listing> expireListings(long now) {
        return database.transaction(connection -> {
            List<Listing> expired = listings(connection,
                    "SELECT * FROM listings WHERE status='ACTIVE' AND expires_at<=?", now);
            List<Listing> changed = new ArrayList<>();
            for (Listing listing : expired) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE listings SET status='EXPIRED',remaining_quantity=0,escrow_state='RETURNED',version=version+1 " +
                                "WHERE id=? AND status='ACTIVE' AND escrow_state='HELD'")) {
                    update.setLong(1, listing.id());
                    if (update.executeUpdate() == 1) {
                        addMailbox(connection, listing.sellerId(), listing.id(), listing.itemBytes(),
                                listing.remainingQuantity(), "EXPIRED", now);
                        changed.add(listing);
                    }
                }
            }
            return changed;
        });
    }

    public boolean cancelListing(long id, UUID actor, boolean admin, String reason) {
        return database.transaction(connection -> {
            Listing listing;
            try (PreparedStatement select = connection.prepareStatement("SELECT * FROM listings WHERE id=?")) {
                select.setLong(1, id);
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next()) return false;
                    listing = readListing(result);
                }
            }
            if (!admin && !listing.sellerId().equals(actor)) return false;
            if (!"ACTIVE".equals(listing.status())) return false;
            String sql = admin
                    ? "UPDATE listings SET status='ADMIN_REMOVED',remaining_quantity=0,escrow_state='RETURNED',version=version+1 WHERE id=? AND status='ACTIVE' AND escrow_state='HELD'"
                    : "UPDATE listings SET status='CANCELLED',version=version+1 WHERE id=? AND status='ACTIVE' AND escrow_state='HELD'";
            try (PreparedStatement update = connection.prepareStatement(sql)) {
                update.setLong(1, id);
                if (update.executeUpdate() != 1) return false;
            }
            if (admin && listing.remainingQuantity() > 0) addMailbox(connection, listing.sellerId(), id,
                    listing.itemBytes(), listing.remainingQuantity(), reason, System.currentTimeMillis());
            return true;
        });
    }

    public boolean restock(long id, UUID owner, int quantity) {
        if (quantity <= 0) return false;
        return database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE listings SET initial_quantity=initial_quantity+?,remaining_quantity=remaining_quantity+?," +
                            "version=version+1 WHERE id=? AND seller_uuid=? AND status IN ('ACTIVE','CANCELLED') AND escrow_state='HELD'")) {
                statement.setInt(1, quantity);
                statement.setInt(2, quantity);
                statement.setLong(3, id);
                statement.setString(4, owner.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean relist(long id, UUID owner, long now, long expiresAt) {
        return database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE listings SET status='ACTIVE',created_at=?,expires_at=?,version=version+1 " +
                            "WHERE id=? AND seller_uuid=? AND status='CANCELLED' AND escrow_state='HELD' AND remaining_quantity>0")) {
                statement.setLong(1, now);
                statement.setLong(2, expiresAt);
                statement.setLong(3, id);
                statement.setString(4, owner.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean deleteListing(long id, UUID owner) {
        return database.transaction(connection -> {
            Listing listing;
            try (PreparedStatement select = connection.prepareStatement("SELECT * FROM listings WHERE id=? AND seller_uuid=?")) {
                select.setLong(1, id);
                select.setString(2, owner.toString());
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next()) return false;
                    listing = readListing(result);
                }
            }
            if (List.of("ACTIVE", "DRAFT", "REVIEW", "DELETED").contains(listing.status())) return false;
            if ("HELD".equals(listing.escrowState()) && listing.remainingQuantity() > 0) {
                addMailbox(connection, owner, id, listing.itemBytes(), listing.remainingQuantity(),
                        "LISTING_DELETED", System.currentTimeMillis());
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE listings SET status='DELETED',remaining_quantity=0,escrow_state='RETURNED',version=version+1 " +
                            "WHERE id=? AND seller_uuid=? AND status=?")) {
                update.setLong(1, id);
                update.setString(2, owner.toString());
                update.setString(3, listing.status());
                return update.executeUpdate() == 1;
            }
        });
    }

    public int clearInactive(UUID owner) {
        List<Listing> rows = database.use(connection -> listings(connection,
                "SELECT * FROM listings WHERE seller_uuid=? AND status IN ('CANCELLED','EXPIRED','SOLD_OUT','ADMIN_REMOVED')",
                owner.toString()));
        int changed = 0;
        for (Listing row : rows) if (deleteListing(row.id(), owner)) changed++;
        return changed;
    }

    public void recoverPreparedSales() {
        database.transaction(connection -> {
            List<long[]> prepared = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id,listing_id,quantity FROM sales WHERE state='PREPARED'")) {
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) prepared.add(new long[]{result.getLong(1), result.getLong(2), result.getLong(3)});
                }
            }
            for (long[] row : prepared) {
                try (PreparedStatement listing = connection.prepareStatement(
                        "UPDATE listings SET remaining_quantity=remaining_quantity+?,status='ACTIVE',escrow_state='HELD',version=version+1 WHERE id=?")) {
                    listing.setLong(1, row[2]);
                    listing.setLong(2, row[1]);
                    listing.executeUpdate();
                }
                try (PreparedStatement sale = connection.prepareStatement(
                        "UPDATE sales SET state='CANCELLED',error='启动时安全释放未扣款预留',updated_at=? WHERE id=?")) {
                    sale.setLong(1, System.currentTimeMillis());
                    sale.setLong(2, row[0]);
                    sale.executeUpdate();
                }
            }
            return null;
        });
    }

    private int update(String sql, Object... parameters) {
        return database.use(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                return statement.executeUpdate();
            }
        });
    }

    private static List<Listing> listings(Connection connection, String sql, Object... parameters) throws SQLException {
        List<Listing> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) { while (result.next()) rows.add(readListing(result)); }
        }
        return rows;
    }

    private static int scalarInt(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
    }

    private static Listing readListing(ResultSet result) throws SQLException {
        return new Listing(result.getLong("id"), UUID.fromString(result.getString("seller_uuid")),
                result.getString("seller_name"), result.getString("market_name"), result.getBytes("item_blob"),
                result.getString("item_fingerprint"), result.getInt("initial_quantity"),
                result.getInt("remaining_quantity"), new BigDecimal(result.getString("unit_price")),
                result.getString("status"), result.getString("escrow_state"), result.getLong("created_at"),
                result.getLong("expires_at"), result.getInt("version"));
    }

    private static SaleRecord readSale(ResultSet result) throws SQLException {
        return new SaleRecord(result.getLong("id"), result.getLong("listing_id"),
                UUID.fromString(result.getString("buyer_uuid")), UUID.fromString(result.getString("seller_uuid")),
                result.getString("market_name"), result.getInt("quantity"),
                new BigDecimal(result.getString("unit_price")), new BigDecimal(result.getString("total")),
                result.getString("state"), result.getLong("created_at"));
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static void addMailbox(Connection connection, UUID owner, Long listingId, byte[] bytes, int quantity,
                                   String reason, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mailbox_items(owner_uuid,listing_id,item_blob,quantity,reason,created_at) VALUES(?,?,?,?,?,?)")) {
            statement.setString(1, owner.toString());
            if (listingId == null) statement.setNull(2, java.sql.Types.BIGINT); else statement.setLong(2, listingId);
            statement.setBytes(3, bytes);
            statement.setInt(4, quantity);
            statement.setString(5, reason);
            statement.setLong(6, now);
            statement.executeUpdate();
        }
    }

    private static void journal(Connection connection, String type, long referenceId, String state, String details,
                                long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO transaction_journal(reference_type,reference_id,state,details,created_at) VALUES(?,?,?,?,?)")) {
            statement.setString(1, type);
            statement.setLong(2, referenceId);
            statement.setString(3, state);
            statement.setString(4, details);
            statement.setLong(5, now);
            statement.executeUpdate();
        }
    }

    private static String listingOrder(String sort) {
        return switch (sort == null ? "LATEST" : sort.toUpperCase()) {
            case "PRICE_ASC" -> "CAST(unit_price AS REAL) ASC,created_at DESC";
            case "PRICE_DESC" -> "CAST(unit_price AS REAL) DESC,created_at DESC";
            case "EXPIRY" -> "expires_at ASC";
            case "NAME" -> "market_name COLLATE NOCASE ASC";
            default -> "created_at DESC";
        };
    }

    public record Reservation(long saleId, Listing listing, int quantity, BigDecimal total) {}
}
