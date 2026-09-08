package fr.ht06.justBoxed.Listeners;

import fr.ht06.justBoxed.Box.Box;
import fr.ht06.justBoxed.Box.BoxRegistry;
import fr.ht06.justBoxed.Box.BoxService;
import fr.ht06.justBoxed.JustBoxed;
import net.kyori.adventure.text.Component;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
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
        Player player = event.getPlayer();
        Advancement advancement = event.getAdvancement();

        // Ignore crafting recipes
        if (advancement.getDisplay() == null) {
            return;
        }

        // Check if the player has a box
        Box box = this.registry.getBoxByPlayer(player.getUniqueId());
        if (box == null) {
            return;
        }

        if (box.hasAdvancement(advancement.getKey())) {
            return;
        }

        this.service.grantAdvancement(box, advancement, player)
                .thenAccept(_ -> {
                    Component msg = player.name()
                                    .append(Component.text(" unlocked the advancement "))
                                    .append(advancement.displayName());
                    box.broadcastMessage(msg);

                });
    }
}
