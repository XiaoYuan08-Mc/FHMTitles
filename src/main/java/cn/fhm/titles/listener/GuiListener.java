package cn.fhm.titles.listener;

import cn.fhm.titles.gui.GuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {

    private final GuiManager gui;

    public GuiListener(GuiManager gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiManager.Holder)) {
            return;
        }
        event.setCancelled(true);
        gui.handleClick(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiManager.Holder)) {
            return;
        }
        event.setCancelled(true);
    }
}
