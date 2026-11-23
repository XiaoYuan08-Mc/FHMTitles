package cn.fhm.titles.gui;

import cn.fhm.titles.config.Settings;
import cn.fhm.titles.core.RewardService;
import cn.fhm.titles.core.TitleService;
import cn.fhm.titles.lang.Lang;
import cn.fhm.titles.model.Category;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.PriceEntry;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.model.TitleRegistry;
import cn.fhm.titles.shop.ShopService;
import cn.fhm.titles.storage.DataStore;
import cn.fhm.titles.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuiManager {

    public enum MenuType {
        MAIN, SHOP, OWNED, ADMIN, REWARDS, CONFIRM
    }

    public static final class Holder implements InventoryHolder {
        public final MenuType type;
        public final Category category;
        public final int page;
        public final String titleId;
        private Inventory inventory;

        private Holder(MenuType type, Category category, int page, String titleId) {
            this.type = type;
            this.category = category;
            this.page = page;
            this.titleId = titleId;
        }

        /** 先构造 holder 再建 inventory，避免 createInventory 构造期回调 getInventory() 拿到 null。 */
        public static Holder create(MenuType type, Category category, int page, String titleId,
                                   int size, Component title) {
            Holder holder = new Holder(type, category, page, titleId);
            holder.inventory = Bukkit.createInventory(holder, size, title);
            return holder;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final JavaPlugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final TitleRegistry registry;
    private final DataStore store;
    private final TitleService titles;
    private final ShopService shop;
    private final InputManager input;
    private final NamespacedKey actionKey;

    public GuiManager(JavaPlugin plugin, Settings settings, Lang lang, TitleRegistry registry,
                      DataStore store, TitleService titles, ShopService shop, InputManager input) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.registry = registry;
        this.store = store;
        this.titles = titles;
        this.shop = shop;
        this.input = input;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
    }

    // ---------------------------------------------------------------- 构建工具

    private ItemStack item(Material material, Component name, List<Component> lore, String action) {
        ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = stack.getItemMeta();
        if (name != null) {
            meta.displayName(name);
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        if (action != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack filler() {
        return item(settings.filler, Component.space(), null, null);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private Component categoryName(Category category) {
        return lang.msg("category." + category.key());
    }

    private Component durationText(Title title) {
        if (title.permanent()) {
            return lang.msg("title.duration-permanent");
        }
        return lang.msg("title.duration-temp", "days", String.valueOf(title.days));
    }

    private Component remainingText(PlayerData data, Title title) {
        long remaining = data.remainingMillis(title.id);
        if (remaining < 0) {
            return lang.msg("title.duration-permanent");
        }
        long days = remaining / 86_400_000L;
        long hours = (remaining % 86_400_000L) / 3_600_000L;
        if (days > 0) {
            return lang.msg("title.remaining", "days", String.valueOf(days), "hours", String.valueOf(hours));
        }
        long minutes = (remaining % 3_600_000L) / 60_000L;
        return lang.msg("title.remaining-short", "hours", String.valueOf(hours), "minutes", String.valueOf(minutes));
    }

    private List<Component> titleLore(Player player, Title title, boolean shopMode) {
        PlayerData data = store.get(player);
        List<Component> lore = new ArrayList<>();
        lore.add(lang.msg("lore.tier", "tier", title.tier.displayName()));
        lore.add(shopMode ? durationText(title) : remainingText(data, title));
        if (!title.prices.isEmpty() && shopMode) {
            for (PriceEntry entry : title.prices) {
                lore.add(TextUtil.parse("<gray>• " + shop.priceText(entry) + "</gray>"));
            }
        }
        if (!title.buffs.isEmpty()) {
            lore.add(lang.msg("lore.buff", "value", String.join(" ", title.buffs)));
        }
        if (!title.particle.isEmpty()) {
            lore.add(lang.msg("lore.particle", "value", title.particle));
        }
        if (title.animated) {
            lore.add(lang.msg("lore.rgb"));
        }
        if (!title.imageGlyph.isEmpty()) {
            lore.add(lang.msg("lore.image"));
        }
        if (!title.lore.isEmpty()) {
            lore.add(Component.empty());
            for (String line : title.lore) {
                lore.add(TextUtil.parse(line));
            }
        }
        if (shopMode) {
            lore.add(durationText(title));
        }
        return lore;
    }

    private Component titleItemName(Title title) {
        Component text = TextUtil.parse(title.text);
        Component suffix = title.suffix.isEmpty() ? Component.empty() : TextUtil.parse(title.suffix);
        return TextUtil.compose("%prefix% %suffix%", Map.of("prefix", text, "suffix", suffix));
    }

    // ---------------------------------------------------------------- 主菜单

    public void openMain(Player player) {
        PlayerData data = store.get(player);
        Holder holder = Holder.create(MenuType.MAIN, null, 0, "", 36, lang.msg("menu.main-title"));
        Inventory inventory = holder.getInventory();

        Title equipped = titles.equipped(player);
        List<Component> ownLore = new ArrayList<>();
        ownLore.add(lang.msg("menu.owned-lore", "count", String.valueOf(data.owned.size())));
        if (equipped != null) {
            Component prefix = titles.display().titleComponent(equipped, player);
            ownLore.add(TextUtil.compose(lang.raw("menu.owned-equipped"), Map.of("title", prefix)));
        }
        inventory.setItem(0, item(Material.CHEST, lang.msg("menu.owned"), ownLore, "open:owned"));
        inventory.setItem(1, item(Material.EMERALD, lang.msg("menu.shop"), List.of(
                lang.msg("menu.shop-lore")), "open:shop:" + Category.TEXT.name()));
        inventory.setItem(2, item(Material.GOLD_INGOT, lang.msg("menu.rewards"), List.of(
                lang.msg("menu.rewards-lore", "count", String.valueOf(rewards().ownedCount(data)))), "open:rewards"));
        inventory.setItem(3, item(Material.SUNFLOWER, lang.msg("menu.coins", "coin", settings.coinName),
                List.of(lang.msg("menu.coins-lore", "amount", String.valueOf(data.coins))), null));

        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            List<Component> lore = new ArrayList<>();
            lore.add(lang.msg("menu.category-lore", "count",
                    String.valueOf(registry.byTag(category).size())));
            inventory.setItem(9 + i, item(category.icon(), categoryName(category), lore,
                    "open:shop:" + category.name()));
        }

        inventory.setItem(18, toggleItem("chat", lang.msg("menu.toggle-chat"), data.showChat));
        inventory.setItem(19, toggleItem("tab", lang.msg("menu.toggle-tab"), data.showTab));
        inventory.setItem(20, toggleItem("head", lang.msg("menu.toggle-head"), data.showHead));

        if (player.hasPermission("fhmtitles.admin")) {
            inventory.setItem(27, item(Material.COMMAND_BLOCK, lang.msg("menu.admin"),
                    List.of(lang.msg("menu.admin-lore")), "open:admin"));
        }
        inventory.setItem(35, item(Material.BARRIER, lang.msg("menu.close"), null, "close"));
        fill(inventory);
        player.openInventory(inventory);
    }

    private RewardService rewards() {
        return titles.rewards();
    }

    private ItemStack toggleItem(String key, Component label, boolean enabled) {
        Component state = enabled ? lang.msg("state.on") : lang.msg("state.off");
        Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        return item(material, TextUtil.compose("%label%: %state%", Map.of("label", label, "state", state)),
                List.of(lang.msg("menu.toggle-lore")), "toggle:" + key);
    }

    // ---------------------------------------------------------------- 商店

    public void openShop(Player player, Category category, int page) {
        List<Title> list = new ArrayList<>();
        PlayerData data = store.get(player);
        for (Title title : registry.byTag(category)) {
            if (!title.enabled) {
                continue;
            }
            if (settings.shopHidePurchased && data.purchased.contains(title.id)) {
                continue;
            }
            list.add(title);
        }
        int pageSize = 45;
        int maxPage = Math.max(0, (list.size() - 1) / pageSize);
        int current = Math.min(Math.max(0, page), maxPage);
        Holder holder = Holder.create(MenuType.SHOP, category, current, "", 54,
                lang.msg("menu.shop-title", "category", lang.raw("category." + category.key())));
        Inventory inventory = holder.getInventory();

        for (int i = 0; i < pageSize; i++) {
            int index = current * pageSize + i;
            if (index >= list.size()) {
                break;
            }
            Title title = list.get(index);
            ItemStack stack = item(title.icon, titleItemName(title), titleLore(player, title, true),
                    "buy:" + title.id + ":" + (title.prices.isEmpty() ? -1 : 0));
            if (title.model > 0) {
                ItemMeta meta = stack.getItemMeta();
                meta.setCustomModelData(title.model);
                stack.setItemMeta(meta);
            }
            inventory.setItem(i, stack);
        }

        inventory.setItem(45, item(Material.ARROW, lang.msg("menu.back"), null, "open:main"));
        if (current > 0) {
            inventory.setItem(48, item(Material.PAPER, lang.msg("menu.prev"), null,
                    "page:shop:" + category.name() + ":" + (current - 1)));
        }
        inventory.setItem(49, item(Material.BOOK, lang.msg("menu.page", "page", String.valueOf(current + 1),
                "max", String.valueOf(maxPage + 1)), null, null));
        if (current < maxPage) {
            inventory.setItem(50, item(Material.PAPER, lang.msg("menu.next"), null,
                    "page:shop:" + category.name() + ":" + (current + 1)));
        }
        inventory.setItem(53, item(Material.BARRIER, lang.msg("menu.close"), null, "close"));
        fill(inventory);
        player.openInventory(inventory);
    }

    public void openOwned(Player player, Category category, int page) {
        PlayerData data = store.get(player);
        List<Title> list = new ArrayList<>();
        for (String id : data.owned.keySet()) {
            if (!data.ownedValid(id)) {
                continue;
            }
            Title title = registry.get(id);
            if (title == null) {
                continue;
            }
            if (category != null && !title.hasTag(category)) {
                continue;
            }
            list.add(title);
        }
        int pageSize = 45;
        int maxPage = Math.max(0, (list.size() - 1) / pageSize);
        int current = Math.min(Math.max(0, page), maxPage);
        Component menuTitle = category == null
                ? lang.msg("menu.owned-title")
                : lang.msg("menu.owned-category", "category", lang.raw("category." + category.key()));
        Holder holder = Holder.create(MenuType.OWNED, category, current, "", 54, menuTitle);
        Inventory inventory = holder.getInventory();

        Title equipped = titles.equipped(player);
        for (int i = 0; i < pageSize; i++) {
            int index = current * pageSize + i;
            if (index >= list.size()) {
                break;
            }
            Title title = list.get(index);
            boolean isEquipped = equipped != null && equipped.id.equals(title.id);
            String action = isEquipped ? "unequip" : "equip:" + title.id;
            ItemStack stack = item(isEquipped ? Material.LIME_STAINED_GLASS_PANE : title.icon,
                    titleItemName(title), titleLore(player, title, false), action);
            if (isEquipped) {
                ItemMeta meta = stack.getItemMeta();
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(0, lang.msg("state.equipped"));
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
            inventory.setItem(i, stack);
        }

        inventory.setItem(45, item(Material.ARROW, lang.msg("menu.back"), null, "open:main"));
        if (current > 0) {
            inventory.setItem(48, item(Material.PAPER, lang.msg("menu.prev"), null,
                    "page:owned:" + (category == null ? "ALL" : category.name()) + ":" + (current - 1)));
        }
        inventory.setItem(49, item(Material.BOOK, lang.msg("menu.page", "page", String.valueOf(current + 1),
                "max", String.valueOf(maxPage + 1)), null, null));
        if (current < maxPage) {
            inventory.setItem(50, item(Material.PAPER, lang.msg("menu.next"), null,
                    "page:owned:" + (category == null ? "ALL" : category.name()) + ":" + (current + 1)));
        }
        inventory.setItem(53, item(Material.BARRIER, lang.msg("menu.close"), null, "close"));
        fill(inventory);
        player.openInventory(inventory);
    }

    // ---------------------------------------------------------------- 购买确认

    public void openConfirm(Player player, Title title) {
        Holder holder = Holder.create(MenuType.CONFIRM, null, 0, title.id, 27,
                lang.msg("menu.confirm-title", "title", TextUtil.plain(titleItemName(title))));
        Inventory inventory = holder.getInventory();
        List<Component> lore = titleLore(player, title, true);
        inventory.setItem(4, item(title.icon, titleItemName(title), lore, null));

        if (title.prices.isEmpty()) {
            inventory.setItem(13, item(Material.EMERALD, lang.msg("menu.buy-free"),
                    List.of(durationText(title)), "buy:" + title.id + ":-1"));
        } else {
            for (int i = 0; i < title.prices.size() && i < 7; i++) {
                PriceEntry entry = title.prices.get(i);
                boolean afford = shop.canAfford(player, entry);
                Material material = switch (entry.type) {
                    case MONEY -> Material.GOLD_NUGGET;
                    case POINTS -> Material.PLAYER_HEAD;
                    case COIN -> Material.SUNFLOWER;
                    case ITEM -> entry.item;
                };
                List<Component> priceLore = new ArrayList<>();
                priceLore.add(TextUtil.parse("<white>" + shop.priceText(entry) + "</white>"));
                priceLore.add(afford ? lang.msg("menu.afford") : lang.msg("menu.not-afford"));
                priceLore.add(durationText(title));
                inventory.setItem(10 + i, item(material,
                        TextUtil.parse(entry.type == cn.fhm.titles.model.PriceType.ITEM
                                ? shop.itemName(entry.item) : lang.raw("menu.price-button")), priceLore,
                        "buy:" + title.id + ":" + i));
            }
        }
        inventory.setItem(22, item(Material.ARROW, lang.msg("menu.back"), null,
                "open:shop:" + title.tags.iterator().next().name()));
        inventory.setItem(26, item(Material.BARRIER, lang.msg("menu.close"), null, "close"));
        fill(inventory);
        player.openInventory(inventory);
    }

    // ---------------------------------------------------------------- 数量奖励

    public void openRewards(Player player) {
        PlayerData data = store.get(player);
        Holder holder = Holder.create(MenuType.REWARDS, null, 0, "", 45, lang.msg("menu.rewards-title"));
        Inventory inventory = holder.getInventory();
        int slot = 9;
        for (RewardService.Reward reward : rewards().rewards()) {
            if (slot >= 36) {
                break;
            }
            boolean claimed = data.claimed.contains("count_" + reward.count());
            boolean reached = rewards().ownedCount(data) >= reward.count();
            Material material = reward.type() == RewardService.RewardType.TITLE ? Material.NAME_TAG : Material.SUNFLOWER;
            List<Component> lore = new ArrayList<>();
            lore.add(lang.msg("reward.need-count", "count", String.valueOf(reward.count())));
            lore.add(reward.type() == RewardService.RewardType.TITLE
                    ? lang.msg("reward.type-title", "value", reward.value())
                    : lang.msg("reward.type-coins", "amount", String.valueOf(reward.amount())));
            lore.add(claimed ? lang.msg("reward.claimed") : (reached ? lang.msg("reward.reached") : lang.msg("reward.not-reached")));
            inventory.setItem(slot++, item(material, lang.msg("reward.item-name", "count", String.valueOf(reward.count())), lore, null));
        }
        inventory.setItem(0, item(Material.ARROW, lang.msg("menu.back"), null, "open:main"));
        inventory.setItem(44, item(Material.BARRIER, lang.msg("menu.close"), null, "close"));
        fill(inventory);
        player.openInventory(inventory);
    }

    // ---------------------------------------------------------------- 管理

    public void openAdmin(Player player, int page) {
        List<Title> list = registry.sortedAll();
        int pageSize = 45;
        int maxPage = Math.max(0, (list.size() - 1) / pageSize);
        int current = Math.min(Math.max(0, page), maxPage);
        Holder holder = Holder.create(MenuType.ADMIN, null, current, "", 54, lang.msg("menu.admin-title"));
        Inventory inventory = holder.getInventory();
        for (int i = 0; i < pageSize; i++) {
            int index = current * pageSize + i;
            if (index >= list.size()) {
                break;
            }
            Title title = list.get(index);
            List<Component> lore = new ArrayList<>();
            lore.add(lang.msg("admin.id", "id", title.id));
            lore.add(lang.msg("admin.state", "state", title.enabled ? lang.raw("state.on-text") : lang.raw("state.off-text")));
            lore.add(lang.msg("admin.tier", "tier", title.tier.displayName()));
            lore.add(durationText(title));
            lore.add(lang.msg("admin.help"));
            inventory.setItem(i, item(title.enabled ? title.icon : Material.GRAY_DYE, titleItemName(title), lore,
                    "admin:toggle:" + title.id));
        }
        inventory.setItem(45, item(Material.ARROW, lang.msg("menu.back"), null, "open:main"));
        if (current > 0) {
            inventory.setItem(48, item(Material.PAPER, lang.msg("menu.prev"), null, "page:admin:" + (current - 1)));
        }
        inventory.setItem(49, item(Material.BOOK, lang.msg("menu.page", "page", String.valueOf(current + 1),
                "max", String.valueOf(maxPage + 1)), null, null));
        if (current < maxPage) {
            inventory.setItem(50, item(Material.PAPER, lang.msg("menu.next"), null, "page:admin:" + (current + 1)));
        }
        inventory.setItem(53, item(Material.BARRIER, lang.msg("menu.close"), null, "close"));
        fill(inventory);
        player.openInventory(inventory);
    }

    // ---------------------------------------------------------------- 点击处理

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.isEmpty()) {
            return;
        }
        String[] parts = action.split(":");
        switch (parts[0]) {
            case "close" -> player.closeInventory();
            case "open" -> {
                if (parts.length < 2) {
                    return;
                }
                switch (parts[1]) {
                    case "main" -> openMain(player);
                    case "owned" -> openOwned(player, null, 0);
                    case "rewards" -> openRewards(player);
                    case "admin" -> {
                        if (player.hasPermission("fhmtitles.admin")) {
                            openAdmin(player, 0);
                        }
                    }
                    case "shop" -> openShop(player, Category.parse(parts[2]), 0);
                    default -> {
                    }
                }
            }
            case "page" -> {
                int page = Integer.parseInt(parts[parts.length - 1]);
                switch (parts[1]) {
                    case "shop" -> openShop(player, Category.parse(parts[2]), page);
                    case "owned" -> openOwned(player, "ALL".equals(parts[2]) ? null : Category.parse(parts[2]), page);
                    case "admin" -> openAdmin(player, page);
                    default -> {
                    }
                }
            }
            case "buy" -> {
                Title title = registry.get(parts[1]);
                if (title == null) {
                    return;
                }
                if (holder.type != MenuType.CONFIRM) {
                    openConfirm(player, title);
                    return;
                }
                int priceIndex = Integer.parseInt(parts[2]);
                if (priceIndex < 0) {
                    titles.grant(player, title.id, title.permanent() ? -1 : title.days, true);
                    player.closeInventory();
                    return;
                }
                if (shop.purchase(player, title, priceIndex)) {
                    player.closeInventory();
                }
            }
            case "equip" -> {
                if (titles.equip(player, parts[1], true)) {
                    player.closeInventory();
                    openOwned(player, null, 0);
                }
            }
            case "unequip" -> {
                titles.unequip(player);
                openOwned(player, null, 0);
            }
            case "toggle" -> {
                PlayerData data = store.get(player);
                switch (parts[1]) {
                    case "chat" -> data.showChat = !data.showChat;
                    case "tab" -> data.showTab = !data.showTab;
                    case "head" -> data.showHead = !data.showHead;
                    default -> {
                        return;
                    }
                }
                store.markDirty(player.getUniqueId());
                titles.display().refresh(player);
                openMain(player);
            }
            case "admin" -> handleAdminAction(player, event, parts);
            default -> {
            }
        }
    }

    private void handleAdminAction(Player player, InventoryClickEvent event, String[] parts) {
        if (!player.hasPermission("fhmtitles.admin") || parts.length < 3) {
            return;
        }
        String id = parts[2];
        Title title = registry.get(id);
        if (title == null) {
            return;
        }
        if (event.isRightClick()) {
            input.request(player, lang.msg("admin.input-text", "id", title.id), text -> {
                title.text = TextUtil.toMiniMessage(text);
                registry.save();
                titles.display().refreshAll();
                player.sendMessage(lang.prefix().append(lang.msg("admin.updated", "id", title.id)));
                openAdmin(player, 0);
            });
            return;
        }
        switch (parts[1]) {
            case "toggle" -> {
                if (event.isShiftClick()) {
                    titles.deleteDefinition(title.id);
                    openAdmin(player, 0);
                    player.sendMessage(lang.prefix().append(lang.msg("admin.deleted", "id", title.id)));
                    return;
                }
                title.enabled = !title.enabled;
                registry.save();
                titles.display().refreshAll();
                openAdmin(player, 0);
                player.sendMessage(lang.prefix().append(lang.msg("admin.updated", "id", title.id)));
            }
            default -> {
            }
        }
    }
}

