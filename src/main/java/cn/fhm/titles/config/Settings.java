package cn.fhm.titles.config;

import cn.fhm.titles.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class Settings {

    private final JavaPlugin plugin;

    public String storageType = "sqlite";
    public String mysqlHost = "127.0.0.1";
    public int mysqlPort = 3306;
    public String mysqlDatabase = "fhm_titles";
    public String mysqlUser = "root";
    public String mysqlPassword = "";
    public String tablePrefix = "fhm_";

    public boolean backupEnabled = true;
    public int backupIntervalMinutes = 360;
    public int backupKeep = 10;
    /** 高频改动（切换佩戴 / 显示开关）的合并写盘间隔（秒）。0 = 这些改动也逐次立即写。 */
    public int flushSeconds = 5;
    public String language = "zh_CN";

    public String chatFormat = "%prefix%<white>%player%</white>%suffix%<dark_gray> » </dark_gray><gray>%message%</gray>";
    public String tabFormat = "%prefix%<white>%player%</white>%suffix%";
    public String headFormat = "%prefix%\\n<white>%player%</white>";
    public boolean headEnabled = true;
    public double headScale = 0.65d;
    public double headYOffset = 0.45d;
    public int headViewDistance = 32;
    public boolean tabEnabled = true;
    public boolean chatEnabled = true;
    /** 列表里要不要显示图片字形：默认 false，图标 16px 比 tab 行高高一倍，会顶成两行 */
    public boolean tabImage = false;

    public int rgbIntervalTicks = 4;
    public int particleIntervalTicks = 10;
    /** 动画称号预渲染的帧数：帧越多越平滑，也越吃内存；48 帧在 4 tick 间隔下已经看不出跳帧。 */
    public int animationFrames = 48;
    public List<String> defaultPalette = new ArrayList<>(List.of("#ff4b4b", "#ffc93c", "#4dd0e1", "#a26bff"));
    public int[] defaultPaletteRgb = new int[0];
    /** 这些世界里称号 BUFF 不生效（命中权限 fhmtitles.buff.bypass-world 的玩家除外）。 */
    public List<String> buffDisabledWorlds = new ArrayList<>();

    public String coinName = "称号币";
    public Material filler = Material.BLACK_STAINED_GLASS_PANE;
    public Material defaultIcon = Material.NAME_TAG;
    public Material cardMaterial = Material.PAPER;
    public Material randomCardMaterial = Material.FIREWORK_STAR;

    public boolean shopHidePurchased = true;
    public boolean expiringWarning = true;
    public int expireCheckSeconds = 30;
    public boolean actionbarOnEquip = true;
    public String acquireMessageType = "CHAT";

    public boolean packEnabled = true;
    public boolean packBuildOnStart = true;
    public boolean packSendOnJoin = true;
    public boolean packRequired = false;
    public String packPrompt = "<gold>服务器提供了称号资源包，用于显示图片称号</gold>";
    public int packPort = 8123;
    public String packPublicUrl = "";
    public int packMinFormat = 65;
    public int packMaxFormat = 200;
    public int glyphCharStart = 0xE000;
    public boolean imageFallbackEnabled = true;
    public String imageFallbackText = "<gray>[图片称号]</gray>";

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        storageType = c.getString("storage.type", "sqlite").toLowerCase();
        mysqlHost = c.getString("storage.mysql.host", "127.0.0.1");
        mysqlPort = c.getInt("storage.mysql.port", 3306);
        mysqlDatabase = c.getString("storage.mysql.database", "fhm_titles");
        mysqlUser = c.getString("storage.mysql.user", "root");
        mysqlPassword = c.getString("storage.mysql.password", "");
        tablePrefix = c.getString("storage.mysql.table-prefix", "fhm_");

        backupEnabled = c.getBoolean("storage.backup.enabled", true);
        backupIntervalMinutes = Math.max(5, c.getInt("storage.backup.interval-minutes", 360));
        backupKeep = Math.max(1, c.getInt("storage.backup.keep", 10));
        flushSeconds = Math.max(0, c.getInt("storage.flush-seconds", 5));
        language = c.getString("language", "zh_CN");

        chatFormat = c.getString("display.chat-format", chatFormat);
        tabFormat = c.getString("display.tab-format", tabFormat);
        headFormat = c.getString("display.head-format", headFormat);
        headEnabled = c.getBoolean("display.head.enabled", true);
        headScale = c.getDouble("display.head.scale", 0.65d);
        headYOffset = c.getDouble("display.head.y-offset", 0.45d);
        headViewDistance = c.getInt("display.head.view-distance", 32);
        tabEnabled = c.getBoolean("display.tab-enabled", true);
        tabImage = c.getBoolean("display.tab-image", false);
        chatEnabled = c.getBoolean("display.chat-enabled", true);

        rgbIntervalTicks = Math.max(1, c.getInt("display.rgb-interval-ticks", 4));
        particleIntervalTicks = Math.max(1, c.getInt("display.particle-interval-ticks", 10));
        animationFrames = Math.max(8, Math.min(240, c.getInt("display.animation-frames", 48)));
        List<String> palette = c.getStringList("display.default-palette");
        if (!palette.isEmpty()) {
            defaultPalette = palette;
        }
        int[] paletteRgb = new int[defaultPalette.size()];
        for (int i = 0; i < defaultPalette.size(); i++) {
            paletteRgb[i] = TextUtil.parseHex(defaultPalette.get(i));
        }
        defaultPaletteRgb = paletteRgb;

        coinName = c.getString("economy.coin-name", "称号币");
        filler = material(c.getString("gui.filler", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
        defaultIcon = material(c.getString("gui.default-icon", "NAME_TAG"), Material.NAME_TAG);
        cardMaterial = material(c.getString("cards.general.material", "PAPER"), Material.PAPER);
        randomCardMaterial = material(c.getString("cards.random.material", "FIREWORK_STAR"), Material.FIREWORK_STAR);

        shopHidePurchased = c.getBoolean("shop.hide-purchased", true);
        expiringWarning = c.getBoolean("title.expire-warning", true);
        expireCheckSeconds = Math.max(5, c.getInt("title.expire-check-seconds", 30));
        actionbarOnEquip = c.getBoolean("title.actionbar-on-equip", true);
        acquireMessageType = c.getString("title.acquire-message-type", "CHAT").toUpperCase();
        buffDisabledWorlds = new ArrayList<>(c.getStringList("title.buff-disabled-worlds"));

        packEnabled = c.getBoolean("resource-pack.enabled", true);
        packBuildOnStart = c.getBoolean("resource-pack.build-on-start", true);
        packSendOnJoin = c.getBoolean("resource-pack.send-on-join", true);
        packRequired = c.getBoolean("resource-pack.required", false);
        packPrompt = c.getString("resource-pack.prompt", packPrompt);
        // 8124：家具插件默认占 8123，两个都装时别再抢同一个端口
        // 顺延最多 10 个端口，上界留出余量，避免 65535 溢出
        packPort = Math.max(1, Math.min(65525, c.getInt("resource-pack.port", 8124)));
        packPublicUrl = c.getString("resource-pack.public-url", "");
        packMinFormat = c.getInt("resource-pack.min-format", 65);
        packMaxFormat = c.getInt("resource-pack.max-format", 200);
        glyphCharStart = c.getInt("resource-pack.char-start", 0xE000);
        imageFallbackEnabled = c.getBoolean("resource-pack.fallback-text-enabled", true);
        imageFallbackText = c.getString("resource-pack.fallback-text", "<gray>[图片称号]</gray>");
    }

    private Material material(String name, Material fallback) {
        Material m = Material.matchMaterial(name == null ? "" : name);
        return m == null ? fallback : m;
    }
}
