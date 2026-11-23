package cn.fhm.titles.item;

import cn.fhm.titles.config.Settings;
import cn.fhm.titles.core.TitleService;
import cn.fhm.titles.lang.Lang;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.Tier;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import cn.fhm.titles.storage.DataStore;
import cn.fhm.titles.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 称号卡：指定称号卡（对应天数）与随机称号卡（按品质抽取）。
 */
public final class CardFactory {

    private final JavaPlugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final TitleRegistry registry;
    private final DataStore store;
    private final TitleService titles;
    private final NamespacedKey keyType;
    private final NamespacedKey keyTitle;
    private final NamespacedKey keyDays;
    private final NamespacedKey keyTier;

    public CardFactory(JavaPlugin plugin, Settings settings, Lang lang, TitleRegistry registry,
                       DataStore store, TitleService titles) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.registry = registry;
        this.store = store;
        this.titles = titles;
        this.keyType = new NamespacedKey(plugin, "card_type");
        this.keyTitle = new NamespacedKey(plugin, "card_title");
        this.keyDays = new NamespacedKey(plugin, "card_days");
        this.keyTier = new NamespacedKey(plugin, "card_tier");
    }

    public ItemStack createTitleCard(Title title, int days, int amount) {
        ItemStack stack = new ItemStack(settings.cardMaterial, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(lang.msg("card.title-name", Map.of("title", TextUtil.parse(title.text))));
        List<Component> lore = new ArrayList<>();
        lore.add(lang.msg("card.title-lore", "id", title.id));
        lore.add(days < 0 ? lang.msg("title.duration-permanent")
                : lang.msg("title.duration-temp", "days", String.valueOf(days)));
        lore.add(lang.msg("card.usage"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, "title");
        meta.getPersistentDataContainer().set(keyTitle, PersistentDataType.STRING, title.id);
        meta.getPersistentDataContainer().set(keyDays, PersistentDataType.INTEGER, days);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack createRandomCard(Tier tier, int amount) {
        ItemStack stack = new ItemStack(settings.randomCardMaterial, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(lang.msg("card.random-name", "tier", tier.displayName()));
        List<Component> lore = new ArrayList<>();
        lore.add(lang.msg("card.random-lore", "tier", tier.displayName()));
        lore.add(lang.msg("card.usage"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, "random");
        meta.getPersistentDataContainer().set(keyTier, PersistentDataType.STRING, tier.name());
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isCard(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().has(keyType, PersistentDataType.STRING);
    }

    /** 使用称号卡，返回是否消耗成功。 */
    public boolean use(Player player, ItemStack stack) {
        return use(player, stack, EquipmentSlot.HAND);
    }

    /** 使用称号卡；hand 指明卡在副手还是主手，消耗时扣对应手上的那张。 */
    public boolean use(Player player, ItemStack stack, EquipmentSlot hand) {
        if (!isCard(stack)) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        String type = meta.getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
        boolean consumed;
        if ("title".equals(type)) {
            String id = meta.getPersistentDataContainer().get(keyTitle, PersistentDataType.STRING);
            int days = meta.getPersistentDataContainer().getOrDefault(keyDays, PersistentDataType.INTEGER, -1);
            Title title = registry.get(id);
            if (title == null) {
                player.sendMessage(lang.prefix().append(lang.msg("error.title-not-found", "id", String.valueOf(id))));
                return false;
            }
            consumed = titles.grant(player, title.id, days, true);
        } else {
            Tier tier = Tier.parse(meta.getPersistentDataContainer().get(keyTier, PersistentDataType.STRING));
            Title picked = pick(tier, store.get(player));
            if (picked == null) {
                player.sendMessage(lang.prefix().append(lang.msg("card.no-pool", "tier", tier.displayName())));
                return false;
            }
            consumed = titles.grant(player, picked.id, picked.permanent() ? -1 : picked.days, true);
            if (consumed) {
                player.sendMessage(lang.prefix().append(lang.msg("card.random-result",
                        "tier", tier.displayName(), "id", picked.id)));
            }
        }
        if (!consumed) {
            return false;
        }
        boolean offhand = hand == EquipmentSlot.OFF_HAND;
        ItemStack held = offhand
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        int amount = held == null || held.getType().isAir() ? 0 : held.getAmount();
        if (amount <= 1) {
            if (offhand) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            held.setAmount(amount - 1);
            if (offhand) {
                player.getInventory().setItemInOffHand(held);
            } else {
                player.getInventory().setItemInMainHand(held);
            }
        }
        player.updateInventory();
        return true;
    }

    private Title pick(Tier tier, PlayerData data) {
        List<Title> pool = new ArrayList<>();
        for (Title title : registry.byTier(tier)) {
            if (title.enabled) {
                pool.add(title);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        List<Title> unowned = new ArrayList<>();
        for (Title title : pool) {
            if (!data.ownedValid(title.id)) {
                unowned.add(title);
            }
        }
        List<Title> source = unowned.isEmpty() ? pool : unowned;
        int total = 0;
        for (Title title : source) {
            total += Math.max(1, title.weight);
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (Title title : source) {
            roll -= Math.max(1, title.weight);
            if (roll < 0) {
                return title;
            }
        }
        return source.get(source.size() - 1);
    }
}

