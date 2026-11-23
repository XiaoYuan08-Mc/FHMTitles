package cn.fhm.titles.model;

import org.bukkit.Material;

public final class PriceEntry {

    public PriceType type = PriceType.MONEY;
    public double amount = 100;
    public Material item = Material.DIAMOND;
    public int itemAmount = 1;
    public String permission = "";

    public PriceEntry() {
    }

    public PriceEntry(PriceType type, double amount) {
        this.type = type;
        this.amount = amount;
    }

    public static PriceEntry ofItem(Material material, int amount) {
        PriceEntry entry = new PriceEntry(PriceType.ITEM, amount);
        entry.item = material;
        entry.itemAmount = amount;
        return entry;
    }
}
