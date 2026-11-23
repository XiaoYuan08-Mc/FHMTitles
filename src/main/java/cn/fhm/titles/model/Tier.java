package cn.fhm.titles.model;

import org.bukkit.Material;

public enum Tier {
    COMMON("普通", "<gray>", Material.GRAY_DYE),
    RARE("稀有", "<blue>", Material.LAPIS_LAZULI),
    EPIC("史诗", "<light_purple>", Material.AMETHYST_SHARD),
    LEGEND("传说", "<gold>", Material.GOLD_INGOT),
    MYTHIC("神话", "<red>", Material.NETHER_STAR);

    private final String displayName;
    private final String color;
    private final Material icon;

    Tier(String displayName, String color, Material icon) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    public Material icon() {
        return icon;
    }

    public static Tier parse(String name) {
        if (name == null) {
            return COMMON;
        }
        for (Tier t : values()) {
            if (t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return COMMON;
    }
}
