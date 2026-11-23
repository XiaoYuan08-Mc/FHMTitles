package cn.fhm.titles.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 称号定义仓库。称号本体存放在 titles.yml，可被后台 GUI / 指令修改后热重载。
 */
public final class TitleRegistry {

    private final JavaPlugin plugin;
    private final Map<String, Title> titles = new LinkedHashMap<>();
    private File file;
    /** 每次载入 / 增删都 +1，供 BUFF 索引这类派生存做失效判断。 */
    private int version = 0;
    private boolean hasParticles = false;

    public TitleRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "titles.yml");
        titles.clear();
        if (!file.exists()) {
            plugin.saveResource("titles.yml", false);
        }
        if (!file.exists()) {
            recount();
            return;
        }
        migrateLegacyIcons(file);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("titles");
        if (root == null) {
            recount();
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            // 统一用小写 ID 作键，避免 titles.yml 里写了大写导致 get() 找不到
            String id = key.toLowerCase(java.util.Locale.ROOT);
            Title title = Title.load(id, section);
            if (title.particle != null && !title.particle.isBlank() && title.particleSpec == null) {
                plugin.getLogger().warning("称号 " + id + " 的粒子配置无法识别，已忽略: " + title.particle);
            }
            if (title.buffs != null && title.potionBuffs.size() != title.buffs.size()) {
                plugin.getLogger().warning("称号 " + id + " 有 BUFF 词条无法识别，已跳过: " + title.buffs);
            }
            titles.put(id, title);
        }
        recount();
    }

    private void recount() {
        version++;
        boolean particles = false;
        for (Title title : titles.values()) {
            if (title.particleSpec != null) {
                particles = true;
                break;
            }
        }
        hasParticles = particles;
    }

    /** 整个称号库的版本号，内容变了才需要重建派生索引。 */
    public int version() {
        return version;
    }

    /** 库里一个带粒子的称号都没有时，粒子周期可以直接空转返回。 */
    public boolean hasParticles() {
        return hasParticles;
    }

    /**
     * 旧版的图片称号是 80×24 的渐变药丸图，文字画在图里，声明成 height 16 之后会被客户端缩到 2/3，
     * 看着就是一团糊。1.1.0 把它换成了 16×16 的图标 + 普通称号文字。
     *
     * 服务器上已有的 titles.yml 不会被新 jar 覆盖（saveResource 不覆盖已存在的文件），
     * 所以这里做一次性文本迁移：换文件名、补上文字、把 ascent 调成 13。
     * 直接改文本而不是读进来再 save()，是为了不动用户自己加的注释。
     */
    private void migrateLegacyIcons(File file) {
        String raw;
        try {
            raw = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        if (!raw.contains("badge_") && !raw.contains("file: glyph.png")) {
            return;
        }
        List<String> lines = new ArrayList<>(List.of(raw.split("\n", -1)));
        int migrated = 0;
        int textLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("  ") && !line.startsWith("   ") && line.trim().endsWith(":")) {
                // 新的顶层称号块，上一个块里的 text 行不再相关
                textLine = -1;
            }
            String trimmed = line.trim();
            if (trimmed.equals("text: \"\"") || trimmed.equals("text: ''")) {
                textLine = i;
                continue;
            }
            if (trimmed.equals("ascent: 14")) {
                lines.set(i, "      ascent: 13");
                continue;
            }
            if (!trimmed.startsWith("file:")) {
                continue;
            }
            String fileName = trimmed.substring("file:".length()).trim();
            String[] replacement = ICON_MIGRATION.get(fileName);
            if (replacement == null) {
                continue;
            }
            lines.set(i, "      file: " + replacement[0]);
            if (textLine >= 0) {
                lines.set(textLine, "    text: \"<gradient:" + replacement[2] + ":" + replacement[3] + ">"
                        + replacement[1] + "</gradient>\"");
            }
            migrated++;
        }
        if (migrated == 0) {
            return;
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), String.join("\n", lines),
                    java.nio.charset.StandardCharsets.UTF_8);
            plugin.getLogger().info("已把 " + migrated + " 个旧版图片称号换成 16×16 图标（badge_*.png → icon_*.png）");
        } catch (IOException e) {
            plugin.getLogger().warning("迁移旧图片称号失败: " + e.getMessage());
        }
    }

    /** 旧图 → 新图标 / 名字 / 渐变起止色 */
    private static final Map<String, String[]> ICON_MIGRATION = Map.of(
            "badge_aurora.png", new String[]{"icon_aurora.png", "极光", "#9a6bff", "#5ce1e6"},
            "badge_star.png", new String[]{"icon_star.png", "星辰", "#ffd75e", "#fff3b0"},
            "badge_flame.png", new String[]{"icon_flame.png", "烈焰", "#ff8a3d", "#ffd166"},
            "badge_orchid.png", new String[]{"icon_orchid.png", "幽兰", "#a97cff", "#ebddff"},
            "badge_flower.png", new String[]{"icon_flower.png", "花语", "#ff8fbf", "#ffd6e6"},
            "badge_thunder.png", new String[]{"icon_thunder.png", "雷帝", "#ffe066", "#fff9c4"},
            "badge_supreme.png", new String[]{"icon_crown.png", "至尊", "#ffcc33", "#fff6cc"},
            "badge_night.png", new String[]{"icon_moon.png", "夜阑", "#9fb0ff", "#e4eaff"},
            "badge_bounty.png", new String[]{"icon_bounty.png", "悬赏", "#ffc23d", "#ffe07a"},
            "glyph.png", new String[]{"icon_glyph.png", "绘梦", "#7fd8ff", "#d8b4ff"}
    );

    public void save() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "titles.yml");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("titles");
        for (Title title : titles.values()) {
            title.save(root.createSection(title.id));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 titles.yml 失败: " + e.getMessage());
        }
    }

    public Title get(String id) {
        if (id == null) {
            return null;
        }
        return titles.get(id.toLowerCase());
    }

    public Collection<Title> all() {
        return titles.values();
    }

    public Map<String, Title> map() {
        return titles;
    }

    public List<Title> byTag(Category category) {
        List<Title> out = new ArrayList<>();
        for (Title title : titles.values()) {
            if (title.hasTag(category)) {
                out.add(title);
            }
        }
        sort(out);
        return out;
    }

    public List<Title> byTier(Tier tier) {
        List<Title> out = new ArrayList<>();
        for (Title title : titles.values()) {
            if (title.tier == tier) {
                out.add(title);
            }
        }
        sort(out);
        return out;
    }

    public List<Title> sortedAll() {
        List<Title> out = new ArrayList<>(titles.values());
        sort(out);
        return out;
    }

    public void sort(List<Title> list) {
        list.sort(Comparator.comparingInt((Title t) -> t.sort).thenComparing(t -> t.id));
    }

    public void register(Title title) {
        titles.put(title.id.toLowerCase(), title);
        title.compile();
        recount();
    }

    public Title remove(String id) {
        Title removed = titles.remove(id == null ? null : id.toLowerCase());
        if (removed != null) {
            recount();
        }
        return removed;
    }

    public Set<String> ids() {
        return titles.keySet();
    }
}
