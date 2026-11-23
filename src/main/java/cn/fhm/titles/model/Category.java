package cn.fhm.titles.model;

import org.bukkit.Material;

public enum Category {
    TEXT("前缀后缀", "text", Material.NAME_TAG),
    BUFF("称号BUFF", "buff", Material.BREWING_STAND),
    PARTICLE("称号粒子", "particle", Material.FIREWORK_STAR),
    IMAGE("图片称号", "image", Material.PAINTING),
    RGB("称号RGB", "rgb", Material.AMETHYST_CLUSTER);

    private final String displayName;
    private final String key;
    private final Material icon;

    Category(String displayName, String key, Material icon) {
        this.displayName = displayName;
        this.key = key;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public String key() {
        return key;
    }

    public Material icon() {
        return icon;
    }

    public static Category parse(String name) {
        if (name == null) {
            return null;
        }
        for (Category c : values()) {
            if (c.name().equalsIgnoreCase(name) || c.key.equalsIgnoreCase(name) || c.displayName.equals(name)) {
                return c;
            }
        }
        return null;
    }
}
