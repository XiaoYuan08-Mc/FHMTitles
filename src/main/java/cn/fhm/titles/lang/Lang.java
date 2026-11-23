package cn.fhm.titles.lang;

import cn.fhm.titles.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Lang {

    private final JavaPlugin plugin;
    private YamlConfiguration config = new YamlConfiguration();
    private String code = "zh_CN";

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String code() {
        return code;
    }

    public void load(String preferred) {
        List<String> candidates = new ArrayList<>();
        if (preferred != null && !preferred.isBlank()) {
            candidates.add(preferred);
        }
        candidates.add("zh_CN");
        candidates.add("en_US");

        for (String candidate : candidates) {
            String path = "lang/" + candidate + ".yml";
            File file = new File(plugin.getDataFolder(), path);
            try {
                if (!file.exists()) {
                    plugin.saveResource(path, false);
                }
                if (!file.exists()) {
                    continue;
                }
                YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
                try (InputStream in = plugin.getResource(path)) {
                    if (in != null) {
                        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                                new InputStreamReader(in, StandardCharsets.UTF_8));
                        loaded.setDefaults(defaults);
                        loaded.options().copyDefaults(true);
                    }
                }
                config = loaded;
                code = candidate;
                return;
            } catch (Exception ignored) {
                // 尝试下一个语种
            }
        }
    }

    public String raw(String key, String... kv) {
        String value = config.getString(key);
        if (value == null) {
            return key;
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            value = value.replace("%" + kv[i] + "%", TextUtil.escape(kv[i + 1]));
        }
        return value;
    }

    public Component msg(String key, String... kv) {
        return TextUtil.parse(raw(key, kv));
    }

    public Component msg(String key, Map<String, Component> rich, String... kv) {
        return TextUtil.compose(raw(key, kv), rich);
    }

    public List<Component> list(String key, String... kv) {
        List<Component> out = new ArrayList<>();
        for (String line : config.getStringList(key)) {
            String value = line;
            for (int i = 0; i + 1 < kv.length; i += 2) {
                value = value.replace("%" + kv[i] + "%", TextUtil.escape(kv[i + 1]));
            }
            out.add(TextUtil.parse(value));
        }
        return out;
    }

    public String prefixText() {
        return config.getString("prefix", "");
    }

    public Component prefix() {
        return TextUtil.parse(prefixText());
    }
}
