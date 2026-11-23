package cn.fhm.titles.listener;

import cn.fhm.titles.item.CardFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class CardListener implements Listener {

    private final CardFactory cards;

    public CardListener(CardFactory cards) {
        this.cards = cards;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!cards.isCard(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        cards.use(event.getPlayer(), event.getItem());
    }
}
