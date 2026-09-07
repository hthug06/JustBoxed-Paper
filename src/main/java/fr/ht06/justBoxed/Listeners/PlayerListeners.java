package fr.ht06.justBoxed.Listeners;

import fr.ht06.justBoxed.Box.Box;
import fr.ht06.justBoxed.Box.BoxRegistry;
import fr.ht06.justBoxed.Box.BoxService;
import fr.ht06.justBoxed.JustBoxed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public class PlayerListeners implements Listener {

    private final JustBoxed plugin;
    private final BoxService service;
    private final BoxRegistry registry;

    public PlayerListeners(JustBoxed plugin, BoxService service, BoxRegistry registry){
        this.plugin = plugin;
        this.service = service;
        this.registry = registry;
    }

    @EventHandler
    public void getAdvancement(PlayerAdvancementDoneEvent event){
        // Check if the player has a box
        if (!this.registry.isPlayerHavingABox(event.getPlayer().getUniqueId())){
            return;
        }

        // Crafting recipes
        if (event.getAdvancement().getDisplay() == null){
            return;
        }

        Box box = this.registry.getBoxByPlayer(event.getPlayer().getUniqueId());

        // TODO sync advancement between box members
        this.service.grantAdvancement(box, event.getAdvancement(), event.getPlayer());
    }
}
