package com.crosstrade.util;

import org.bukkit.inventory.ItemStack;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ItemCodec {
    public byte[] encodePrototype(ItemStack source) {
        ItemStack prototype = source.clone();
        prototype.setAmount(1);
        return prototype.serializeAsBytes();
    }

    public ItemStack decode(byte[] bytes) {
        ItemStack result = ItemStack.deserializeBytes(bytes);
        result.setAmount(1);
        return result;
    }

    public String fingerprint(ItemStack source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encodePrototype(source)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
