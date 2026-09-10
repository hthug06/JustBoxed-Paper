package fr.ht06.justBoxed.Listeners;

import fr.ht06.justBoxed.Box.Box;
import fr.ht06.justBoxed.Box.BoxRegistry;
import fr.ht06.justBoxed.Box.BoxService;
import fr.ht06.justBoxed.JustBoxed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.Set;
import java.util.UUID;

public class PlayerListeners implements Listener {

    private final BoxService service;
    private final BoxRegistry registry;

    public PlayerListeners(BoxService service, BoxRegistry registry){
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

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }

        Player player = event.getPlayer();
        Box box = this.registry.getBoxByPlayer(player.getUniqueId());
        if (box == null) {
            return;
        }

        World fromWorld = player.getWorld();
        World targetWorld;
        double targetX;
        double targetZ;

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            // Overworld -> Nether
            targetWorld = this.service.loadWorld(box, World.Environment.NETHER);
            if (targetWorld == null) {
                event.setCancelled(true);
                return;
            }
            targetX = event.getFrom().getX() / 8.0;
            targetZ = event.getFrom().getZ() / 8.0;

        } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
            // Nether -> Overworld
            targetWorld = this.service.loadWorld(box, World.Environment.NORMAL);
            if (targetWorld == null) {
                event.setCancelled(true);
                return;
            }
            targetX = event.getFrom().getX() * 8.0;
            targetZ = event.getFrom().getZ() * 8.0;

        } else {
            return;
        }

        // Calculation of permitted limits according to the WorldBorder
        WorldBorder border = targetWorld.getWorldBorder();
        Location center = border.getCenter();
        double halfSize = (border.getSize() / 2.0) - 2.0;

        double minX = center.getX() - halfSize;
        double maxX = center.getX() + halfSize;
        double minZ = center.getZ() - halfSize;
        double maxZ = center.getZ() + halfSize;

        // Clamping to stay inside
        targetX = Math.clamp(targetX, minX, maxX);
        targetZ = Math.clamp(targetZ, minZ, maxZ);

        Location targetLocation = new Location(targetWorld, targetX, event.getFrom().getY(), targetZ);
        event.setTo(targetLocation);

        //Create portal
        int safeRadius = (int) Math.floor(Math.min(
                Math.min(targetX - minX, maxX - targetX),
                Math.min(targetZ - minZ, maxZ - targetZ)
        ));
        safeRadius = Math.max(1, safeRadius);

        event.setCanCreatePortal(true);
        event.setCreationRadius(safeRadius);
        event.setSearchRadius(128);
    }
}
