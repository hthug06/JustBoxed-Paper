package fr.ht06.justBoxed.Listeners;

import fr.ht06.justBoxed.Box.Box;
import fr.ht06.justBoxed.Box.BoxRegistry;
import fr.ht06.justBoxed.Box.BoxService;
import fr.ht06.justBoxed.JustBoxed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;

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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Box box = this.registry.getBoxByPlayer(player.getUniqueId());
        if (box == null) return;

        Set<Advancement> advancements = this.service.syncEveryAdvancementForOnePlayer(box, player);
        if (!advancements.isEmpty()){
            // Create a component to show how many advancements where made while the player was offline
            Component advancementsList = Component.join(
                    JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)),
                    advancements.stream().map(Advancement::displayName).toList()
            );

            Component message = Component.text("While you were offline, your box members completed a total of ", NamedTextColor.GRAY)
                    .append(Component.text(advancements.size(), NamedTextColor.GOLD)
                            .hoverEvent(HoverEvent.showText(advancementsList)))
                    .append(Component.text(" advancements.", NamedTextColor.GRAY));

            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();

        // Remove invitations where this player is the target
        for (Box box : this.registry.getAllBoxes()) {
            if (box.isInvited(playerUuid)) {
                box.removeInvitation(playerUuid);
            }
        }
    }
}
