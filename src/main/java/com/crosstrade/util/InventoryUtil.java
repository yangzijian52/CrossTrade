package com.crosstrade.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InventoryUtil {
    private InventoryUtil() {}

    public static int countSimilar(PlayerInventory inventory, ItemStack prototype) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && !item.getType().isAir() && item.isSimilar(prototype)) count += item.getAmount();
        }
        return count;
    }

    public static boolean removeSimilar(PlayerInventory inventory, ItemStack prototype, int amount) {
        if (amount <= 0 || countSimilar(inventory, prototype) < amount) return false;
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.isSimilar(prototype)) continue;
            int take = Math.min(remaining, item.getAmount());
            if (take == item.getAmount()) contents[i] = null;
            else item.setAmount(item.getAmount() - take);
            remaining -= take;
        }
        inventory.setStorageContents(contents);
        return remaining == 0;
    }

    public static List<ItemStack> split(ItemStack prototype, int amount) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        int max = Math.max(1, prototype.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = prototype.clone();
            stack.setAmount(Math.min(max, remaining));
            result.add(stack);
            remaining -= stack.getAmount();
        }
        return result;
    }

    public static boolean canFit(PlayerInventory inventory, ItemStack prototype, int amount) {
        ItemStack[] simulated = cloneItems(inventory.getStorageContents());
        return canFit(simulated, split(prototype, amount));
    }

    public static boolean canFit(PlayerInventory inventory, List<ItemStack> items) {
        return canFit(cloneItems(inventory.getStorageContents()), items);
    }

    private static boolean canFit(ItemStack[] slots, List<ItemStack> additions) {
        for (ItemStack addition : additions) {
            int remaining = addition.getAmount();
            for (ItemStack slot : slots) {
                if (slot != null && slot.isSimilar(addition) && slot.getAmount() < slot.getMaxStackSize()) {
                    int moved = Math.min(remaining, slot.getMaxStackSize() - slot.getAmount());
                    slot.setAmount(slot.getAmount() + moved);
                    remaining -= moved;
                    if (remaining == 0) break;
                }
            }
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                if (slots[i] != null && !slots[i].getType().isAir()) continue;
                ItemStack placed = addition.clone();
                placed.setAmount(Math.min(remaining, placed.getMaxStackSize()));
                slots[i] = placed;
                remaining -= placed.getAmount();
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    public static List<ItemStack> add(PlayerInventory inventory, ItemStack prototype, int amount) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack stack : split(prototype, amount)) {
            Map<Integer, ItemStack> result = inventory.addItem(stack);
            leftovers.addAll(result.values());
        }
        return leftovers;
    }

    public static ItemStack[] cloneItems(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return copy;
    }
}
