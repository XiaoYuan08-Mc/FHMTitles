package cn.fhm.titles.papi;

import cn.fhm.titles.FHMTitlesPlugin;
import cn.fhm.titles.model.PlayerData;
import cn.fhm.titles.model.Title;
import cn.fhm.titles.util.TextUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

public final class FHMTitleExpansion extends PlaceholderExpansion {

    private final FHMTitlesPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public FHMTitleExpansion(FHMTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "fhmtitles";
    }

    @Override
    public String getAuthor() {
        return "FHM";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        PlayerData data = plugin.store().get(player.getUniqueId());
        Title title = plugin.display().equippedTitle(data);
        String key = params.toLowerCase(Locale.ROOT);
        if (key.startsWith("has_")) {
            return data.ownedValid(key.substring(4)) ? "yes" : "no";
        }
        return switch (key) {
            case "display", "prefix" -> title == null ? "" : legacy.serialize(plugin.display().titleComponent(title));
            case "suffix" -> title == null || title.suffix.isEmpty() ? "" : legacy.serialize(TextUtil.parse(title.suffix));
            case "name" -> title == null ? "" : TextUtil.plain(plugin.display().titleComponent(title));
            case "equipped" -> data.equipped;
            case "count" -> String.valueOf(plugin.titles().rewards().ownedCount(data));
            case "coins" -> String.valueOf(data.coins);
            case "chat" -> data.showChat ? "on" : "off";
            case "tab" -> data.showTab ? "on" : "off";
            case "head" -> data.showHead ? "on" : "off";
            default -> "";
        };
    }
}
