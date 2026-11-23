package cn.fhm.titles.command;

import cn.fhm.titles.FHMTitlesPlugin;
import cn.fhm.titles.lang.Lang;
import cn.fhm.titles.model.Category;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.Tier;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TitleCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("help", "menu", "shop", "list", "info", "equip", "unequip",
            "give", "take", "card", "randomcard", "coins", "create", "delete", "reload", "toggle", "pack", "image");

    private final FHMTitlesPlugin plugin;

    public TitleCommand(FHMTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    private Lang lang() {
        return plugin.lang();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!allow(sender, "fhmtitles.use")) {
                    return true;
                }
                plugin.gui().openMain(player);
            } else {
                help(sender);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help", "?" -> help(sender);
            case "menu", "gui", "open" -> {
                Player player = asPlayer(sender);
                if (player == null || !allow(sender, "fhmtitles.use")) {
                    return true;
                }
                plugin.gui().openMain(player);
            }
            case "shop" -> {
                Player player = asPlayer(sender);
                if (player == null || !allow(sender, "fhmtitles.shop")) {
                    return true;
                }
                Category category = args.length > 1 ? Category.parse(args[1]) : Category.TEXT;
                plugin.gui().openShop(player, category == null ? Category.TEXT : category, 0);
            }
            case "list" -> {
                if (allow(sender, "fhmtitles.use")) {
                    list(sender, args);
                }
            }
            case "info" -> {
                if (allow(sender, "fhmtitles.use")) {
                    info(sender, args);
                }
            }
            case "equip" -> {
                if (allow(sender, "fhmtitles.equip")) {
                    equip(sender, args);
                }
            }
            case "unequip" -> {
                Player player = asPlayer(sender);
                if (player == null || !allow(sender, "fhmtitles.equip")) {
                    return true;
                }
                plugin.titles().unequip(player);
                player.sendMessage(lang().prefix().append(lang().msg("cmd.unequipped")));
            }
            case "give" -> give(sender, args);
            case "take" -> take(sender, args);
            case "card" -> card(sender, args);
            case "randomcard" -> randomCard(sender, args);
            case "coins" -> coins(sender, args);
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "reload" -> reload(sender);
            case "toggle" -> {
                if (allow(sender, "fhmtitles.use")) {
                    toggle(sender, args);
                }
            }
            case "pack" -> pack(sender, args);
            case "image" -> image(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.help-title")));
        for (Component line : lang().list("cmd.help-lines")) {
            sender.sendMessage(line);
        }
    }

    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.player-only")));
        return null;
    }

    private boolean admin(CommandSender sender) {
        if (!sender.hasPermission("fhmtitles.admin")) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.no-permission")));
            return false;
        }
        return true;
    }

    /** 普通玩家权限检查（权限在 plugin.yml 里声明，默认 true）。 */
    private boolean allow(CommandSender sender, String node) {
        if (sender.hasPermission(node)) {
            return true;
        }
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.no-permission")));
        return false;
    }

    private void list(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        PlayerData data = plugin.store().get(player);
        Category filter = args.length > 1 ? Category.parse(args[1]) : null;
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.list-header",
                "count", String.valueOf(data.owned.size()))));
        for (String id : data.owned.keySet()) {
            Title title = plugin.registry().get(id);
            if (title == null) {
                continue;
            }
            if (filter != null && !title.hasTag(filter)) {
                continue;
            }
            boolean equipped = id.equals(data.equipped);
            long remaining = data.remainingMillis(id);
            String remainText = remaining < 0 ? lang().raw("cmd.permanent")
                    : lang().raw("cmd.remaining", "days", String.valueOf(remaining / 86_400_000L));
            java.util.Map<String, Component> rich = java.util.Map.of(
                    "title", plugin.display().titleComponent(title, player),
                    "state", equipped ? lang().msg("state.equipped") : Component.empty());
            sender.sendMessage(lang().msg("cmd.list-line", rich, "id", id, "remaining", remainText));
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles info <称号ID>")));
            return;
        }
        Title title = plugin.registry().get(args[1]);
        if (title == null) {
            sender.sendMessage(lang().prefix().append(lang().msg("error.title-not-found", "id", args[1])));
            return;
        }
        sender.sendMessage(lang().msg("cmd.info-header").append(plugin.display().titleComponent(title,
                sender instanceof Player p ? p : null)));
        sender.sendMessage(lang().msg("cmd.info-id", "id", title.id));
        sender.sendMessage(lang().msg("cmd.info-tags", "tags", String.join(",", title.tags.stream().map(Enum::name).toList())));
        sender.sendMessage(lang().msg("cmd.info-tier", "tier", title.tier.displayName()));
        sender.sendMessage(lang().msg("cmd.info-days", "days",
                title.permanent() ? lang().raw("cmd.permanent") : String.valueOf(title.days)));
        sender.sendMessage(lang().msg("cmd.info-buffs", "value", String.join(",", title.buffs)));
        sender.sendMessage(lang().msg("cmd.info-particle", "value", title.particle.isEmpty() ? "-" : title.particle));
        if (title.prices.isEmpty()) {
            sender.sendMessage(lang().msg("cmd.info-prices", "value", "-"));
        } else {
            sender.sendMessage(lang().msg("cmd.info-prices-header"));
            for (var entry : title.prices) {
                Component price = TextUtil.parse(plugin.shop().priceText(entry));
                sender.sendMessage(lang().msg("cmd.info-price-line", java.util.Map.of("price", price),
                        "type", entry.type.name()));
            }
        }
    }

    private void equip(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles equip <称号ID>")));
            return;
        }
        plugin.titles().equip(player, args[1], true);
    }

    private Player target(CommandSender sender, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.player-offline", "player", name)));
        }
        return player;
    }

    /** 发放 / 回收 / 改币的目标：在线优先，离线也能通过 usercache 定位到 UUID。 */
    private record Target(Player online, PlayerData data, String name) {
        boolean isOnline() {
            return online != null;
        }
    }

    private Target resolveTarget(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Target(online, plugin.store().get(online), online.getName());
        }
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline == null) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.player-offline", "player", name)));
            return null;
        }
        PlayerData data = plugin.store().get(offline.getUniqueId());
        String display = offline.getName() == null ? name : offline.getName();
        data.name = display;
        return new Target(null, data, display);
    }

    private void give(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles give <玩家> <称号ID> [天数]")));
            return;
        }
        Target target = resolveTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        Title title = plugin.registry().get(args[2]);
        if (title == null) {
            sender.sendMessage(lang().prefix().append(lang().msg("error.title-not-found", "id", args[2])));
            return;
        }
        int days = args.length > 3 ? parseInt(args[3], title.days) : title.days;
        boolean ok = target.isOnline()
                ? plugin.titles().grant(target.online(), title.id, days, true)
                : plugin.titles().grantOffline(target.data(), title.id, days);
        if (!ok) {
            sender.sendMessage(lang().prefix().append(lang().msg("shop.unavailable")));
            return;
        }
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.given", "player", target.name(), "id", title.id)));
    }

    private void take(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles take <玩家> <称号ID>")));
            return;
        }
        Target target = resolveTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        boolean removed = target.isOnline()
                ? plugin.titles().revoke(target.online(), args[2])
                : plugin.titles().revokeOffline(target.data(), args[2]);
        if (removed) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.taken", "player", target.name(), "id", args[2])));
        } else {
            sender.sendMessage(lang().prefix().append(lang().msg("error.not-owned", "title", args[2])));
        }
    }

    private void card(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles card <玩家> <称号ID> [天数] [数量]")));
            return;
        }
        Player target = target(sender, args[1]);
        if (target == null) {
            return;
        }
        Title title = plugin.registry().get(args[2]);
        if (title == null) {
            sender.sendMessage(lang().prefix().append(lang().msg("error.title-not-found", "id", args[2])));
            return;
        }
        int days = args.length > 3 ? parseInt(args[3], title.days) : title.days;
        int amount = args.length > 4 ? parseInt(args[4], 1) : 1;
        ItemStack stack = plugin.cards().createTitleCard(title, days, amount);
        giveItem(target, stack);
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.card-given", "player", target.getName(),
                "id", title.id, "amount", String.valueOf(amount))));
    }

    private void randomCard(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles randomcard <玩家> <品质> [数量]")));
            return;
        }
        Player target = target(sender, args[1]);
        if (target == null) {
            return;
        }
        Tier tier = Tier.parse(args[2]);
        int amount = args.length > 3 ? parseInt(args[3], 1) : 1;
        ItemStack stack = plugin.cards().createRandomCard(tier, amount);
        giveItem(target, stack);
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.card-given", "player", target.getName(),
                "id", "RANDOM:" + tier.name(), "amount", String.valueOf(amount))));
    }

    private void giveItem(Player target, ItemStack stack) {
        List<ItemStack> leftover = target.getInventory().addItem(stack).values().stream().toList();
        for (ItemStack drop : leftover) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }
    }

    private void coins(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles coins <玩家> <give|take|set> <数量>")));
            return;
        }
        Target target = resolveTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        PlayerData data = target.data();
        // 用 long 解析：int 会被 5000000000 这种数字直接溢出成 0
        long amount = parseLong(args[3], 0);
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "take", "sub" -> data.coins = Math.max(0, data.coins - amount);
            case "set" -> data.coins = Math.max(0, amount);
            default -> data.coins += amount;
        }
        plugin.store().markDirty(data.uuid);
        plugin.store().save(data.uuid);
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.coins", "player", target.name(),
                "amount", String.valueOf(data.coins), "coin", plugin.settings().coinName)));
    }

    private void create(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles create <ID> <称号文本>")));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,32}")) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.bad-id")));
            return;
        }
        if (plugin.registry().get(id) != null) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.id-exists", "id", id)));
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Title title = new Title(id);
        title.text = TextUtil.toMiniMessage(text);
        title.icon = plugin.settings().defaultIcon;
        title.tags = java.util.EnumSet.of(Category.TEXT);
        plugin.registry().register(title);
        plugin.registry().save();
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.created", "id", id)));
    }

    private void delete(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles delete <称号ID>")));
            return;
        }
        Title title = plugin.registry().get(args[1]);
        if (title == null) {
            sender.sendMessage(lang().prefix().append(lang().msg("error.title-not-found", "id", args[1])));
            return;
        }
        plugin.titles().deleteDefinition(title.id);
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.deleted", "id", title.id)));
    }

    private void pack(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        var images = plugin.images();
        if (images == null) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.pack-disabled")));
            return;
        }
        if (args.length > 1 && "send".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles pack send <玩家|all>")));
                return;
            }
            if ("all".equalsIgnoreCase(args[2])) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    images.send(player);
                }
                sender.sendMessage(lang().prefix().append(lang().msg("cmd.pack-sent-all",
                        "amount", String.valueOf(Bukkit.getOnlinePlayers().size()))));
            } else {
                Player target = target(sender, args[2]);
                if (target == null) {
                    return;
                }
                images.send(target);
                sender.sendMessage(lang().prefix().append(lang().msg("cmd.pack-sent", "player", target.getName())));
            }
            return;
        }
        var result = images.build();
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.pack-built",
                "count", String.valueOf(result.counts()),
                "size", String.valueOf(result.bytes() / 1024),
                "hash", result.hash().isEmpty() ? "-" : result.hash().substring(0, Math.min(8, result.hash().length())))));
        sender.sendMessage(lang().msg("cmd.pack-url", "url", result.url().isEmpty() ? "-" : result.url()));
        for (String problem : result.problems()) {
            sender.sendMessage(lang().msg("cmd.pack-problem", "value", problem));
        }
    }

    private void image(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage",
                    "usage", "/titles image <称号ID> <图片文件名> [字形数] [高度] [ascent]")));
            return;
        }
        if (plugin.images() == null) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.pack-disabled")));
            return;
        }
        int glyphs = args.length > 3 ? parseInt(args[3], 1) : 1;
        int height = args.length > 4 ? parseInt(args[4], 9) : 9;
        int ascent = args.length > 5 ? parseInt(args[5], 7) : 7;
        String error = plugin.images().assignImage(args[1], args[2], glyphs, height, ascent);
        if (error != null) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.image-failed", "value", error)));
            return;
        }
        Title title = plugin.registry().get(args[1]);
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.image-set", "id", args[1], "file", args[2])));
        if (title != null) {
            sender.sendMessage(lang().msg("cmd.info-header").append(plugin.display().titleComponent(title,
                    sender instanceof Player p ? p : null)));
        }
        plugin.display().refreshAll();
    }

    private void reload(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(lang().prefix().append(lang().msg("cmd.reloaded")));
    }

    private void toggle(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles toggle <chat|tab|head>")));
            return;
        }
        PlayerData data = plugin.store().get(player);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "chat" -> data.showChat = !data.showChat;
            case "tab" -> data.showTab = !data.showTab;
            case "head" -> data.showHead = !data.showHead;
            default -> {
                sender.sendMessage(lang().prefix().append(lang().msg("cmd.usage", "usage", "/titles toggle <chat|tab|head>")));
                return;
            }
        }
        plugin.store().markDirty(player.getUniqueId());
        plugin.display().refresh(player);
        player.sendMessage(lang().prefix().append(lang().msg("cmd.toggled")));
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "pack" -> {
                    return filter(List.of("send"), args[1]);
                }
                case "image" -> {
                    return filter(new ArrayList<>(plugin.registry().ids()), args[1]);
                }
                case "shop", "list", "toggle" -> {
                    List<String> options = new ArrayList<>();
                    if ("toggle".equals(sub)) {
                        options.addAll(List.of("chat", "tab", "head"));
                    } else {
                        for (Category category : Category.values()) {
                            options.add(category.name());
                        }
                    }
                    return filter(options, args[1]);
                }
                case "equip", "info", "delete" -> {
                    return filter(new ArrayList<>(plugin.registry().ids()), args[1]);
                }
                case "give", "take", "card", "randomcard", "coins" -> {
                    List<String> names = new ArrayList<>();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        names.add(player.getName());
                    }
                    return filter(names, args[1]);
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3) {
            if ("pack".equals(sub)) {
                List<String> options = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    options.add(player.getName());
                }
                options.add("all");
                return filter(options, args[2]);
            }
            if ("image".equals(sub) && plugin.images() != null) {
                File[] files = plugin.images().imagesDir().listFiles();
                List<String> names = new ArrayList<>();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            names.add(file.getName());
                        }
                    }
                }
                return filter(names, args[2]);
            }
            if ("give".equals(sub) || "card".equals(sub)) {
                return filter(new ArrayList<>(plugin.registry().ids()), args[2]);
            }
            if ("randomcard".equals(sub)) {
                List<String> tiers = new ArrayList<>();
                for (Tier tier : Tier.values()) {
                    tiers.add(tier.name());
                }
                return filter(tiers, args[2]);
            }
            if ("coins".equals(sub)) {
                return filter(List.of("give", "take", "set"), args[2]);
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                out.add(option);
            }
        }
        return out;
    }
}
