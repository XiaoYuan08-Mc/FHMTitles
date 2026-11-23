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
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("titles");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            // 统一用小写 ID 作键，避免 titles.yml 里写了大写导致 get() 找不到
            String id = key.toLowerCase(java.util.Locale.ROOT);
            titles.put(id, Title.load(id, section));
        }
    }

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
    }

    public Title remove(String id) {
        return titles.remove(id == null ? null : id.toLowerCase());
    }

    public Set<String> ids() {
        return titles.keySet();
    }
}
