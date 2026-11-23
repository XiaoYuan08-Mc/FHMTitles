package cn.fhm.titles.shop;

import cn.fhm.titles.config.Settings;
import cn.fhm.titles.core.TitleService;
import cn.fhm.titles.econ.PointsHook;
import cn.fhm.titles.econ.VaultHook;
import cn.fhm.titles.lang.Lang;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.PriceEntry;
import cn.fhm.titles.model.PriceType;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.storage.DataStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class ShopService {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final DataStore store;
    private final TitleService titles;
    private final VaultHook vault = new VaultHook();
    private final PointsHook points = new PointsHook();

    public ShopService(JavaPlugin plugin, Settings settings, Lang lang, DataStore store, TitleService titles) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.store = store;
        this.titles = titles;
    }

    public VaultHook vault() {
        return vault;
    }

    public PointsHook points() {
        return points;
    }

    public boolean canAfford(Player player, PriceEntry entry) {
        if (entry == null) {
            return true;
        }
        return switch (entry.type) {
            case MONEY -> vault.available() && vault.balance(player) >= entry.amount;
            case POINTS -> points.available() && points.balance(player) >= (int) entry.amount;
            case COIN -> store.get(player).coins >= (long) entry.amount;
            case ITEM -> countItem(player, entry.item) >= itemAmount(entry);
        };
    }

    public int itemAmount(PriceEntry entry) {
        return entry.itemAmount > 0 ? entry.itemAmount : (int) entry.amount;
    }

    public int countItem(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public String priceText(PriceEntry entry) {
        if (entry == null) {
            return lang.raw("price.free");
        }
        return switch (entry.type) {
            case MONEY -> lang.raw("price.money", "amount", format(entry.amount));
            case POINTS -> lang.raw("price.points", "amount", format(entry.amount));
            case COIN -> lang.raw("price.coin", "amount", format(entry.amount));
            case ITEM -> lang.raw("price.item", "amount", String.valueOf(itemAmount(entry)),
                    "item", itemName(entry.item));
        };
    }

    public String itemName(Material material) {
        return material == null ? "?" : material.name();
    }

    public String format(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }

    /** 执行购买。priceIndex 为 title.prices 下标，-1 表示免费。 */
    public boolean purchase(Player player, Title title, int priceIndex) {
        if (!title.enabled) {
            player.sendMessage(lang.prefix().append(lang.msg("shop.unavailable")));
            return false;
        }
        if (!title.permission.isEmpty() && !player.hasPermission(title.permission)) {
            player.sendMessage(lang.prefix().append(lang.msg("error.no-permission-title", "title", title.id)));
            return false;
        }
        PriceEntry entry = priceIndex < 0 || priceIndex >= title.prices.size() ? null : title.prices.get(priceIndex);
        if (entry != null) {
            if (!pay(player, entry)) {
                return false;
            }
        }
        int days = title.permanent() ? -1 : title.days;
        boolean granted = titles.grant(player, title.id, days, true);
        if (!granted) {
            // 已经扣过款就退回去，别让玩家掏了钱什么都没拿到
            if (entry != null) {
                refund(player, entry);
            }
            player.sendMessage(lang.prefix().append(lang.msg("shop.unavailable")));
            return false;
        }
        Map<String, Component> rich = new HashMap<>();
        rich.put("title", titles.display().titleComponent(title, player));
        player.sendMessage(lang.prefix().append(lang.msg("shop.bought", rich)));
        return true;
    }

    public boolean pay(Player player, PriceEntry entry) {
        switch (entry.type) {
            case MONEY -> {
                if (!vault.available()) {
                    player.sendMessage(lang.prefix().append(lang.msg("error.vault-missing")));
                    return false;
                }
                if (!vault.withdraw(player, entry.amount)) {
                    player.sendMessage(lang.prefix().append(lang.msg("error.not-enough-money", "amount", format(entry.amount))));
                    return false;
                }
                return true;
            }
            case POINTS -> {
                if (!points.available()) {
                    player.sendMessage(lang.prefix().append(lang.msg("error.points-missing")));
                    return false;
                }
                if (!points.take(player, (int) entry.amount)) {
                    player.sendMessage(lang.prefix().append(lang.msg("error.not-enough-points", "amount", format(entry.amount))));
                    return false;
                }
                return true;
            }
            case COIN -> {
                PlayerData data = store.get(player);
                if (data.coins < (long) entry.amount) {
                    player.sendMessage(lang.prefix().append(lang.msg("error.not-enough-coin",
                            "amount", format(entry.amount), "coin", settings.coinName)));
                    return false;
                }
                data.coins -= (long) entry.amount;
                store.markDirty(player.getUniqueId());
                return true;
            }
            case ITEM -> {
                int need = itemAmount(entry);
                if (!takeItems(player, entry.item, need)) {
                    player.sendMessage(lang.prefix().append(lang.msg("error.not-enough-item",
                            "amount", String.valueOf(need), "item", itemName(entry.item))));
                    return false;
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    /** 购买在扣款后失败时的退款路径。 */
    private void refund(Player player, PriceEntry entry) {
        switch (entry.type) {
            case MONEY -> vault.deposit(player, entry.amount);
            case POINTS -> points.give(player, (int) entry.amount);
            case COIN -> {
                PlayerData data = store.get(player);
                data.coins += (long) entry.amount;
                store.markDirty(player.getUniqueId());
                store.save(player.getUniqueId());
            }
            case ITEM -> {
                int amount = itemAmount(entry);
                if (entry.item != null && amount > 0) {
                    ItemStack back = new ItemStack(entry.item, amount);
                    for (ItemStack leftover : player.getInventory().addItem(back).values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    }
                }
            }
            default -> {
            }
        }
    }

    private boolean takeItems(Player player, Material material, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (countItem(player, material) < amount) {
            return false;
        }
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            int left = stack.getAmount() - take;
            if (left <= 0) {
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(left);
                player.getInventory().setItem(i, stack);
            }
            remaining -= take;
        }
        player.updateInventory();
        return remaining == 0;
    }
}
