package fr.ht06.justBoxed.Box;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/// An invitation sent to a player to join a box
/// Autodestruct itself after 60 seconds
public class BoxInvite {

    private final UUID targetUuid;
    private final BukkitTask expirationTask;

    public BoxInvite(Plugin plugin, Box box, UUID inviterUuid, UUID targetUuid, Runnable onExpire) {
        this.targetUuid = targetUuid;

        this.expirationTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null && target.isOnline()) {
                target.sendMessage(Component.text("The invitation for the box '")
                        .append(box.getDisplayName())
                        .append(Component.text("' expired.")));
            }

            Player inviter = Bukkit.getPlayer(inviterUuid);
            if (inviter != null && inviter.isOnline() && target != null) {
                inviter.sendMessage(Component.text("Your invitation sent to ")
                        .append(target.name())
                        .append(Component.text(" expired.")));
            }

            if (onExpire != null) {
                onExpire.run();
            }
        }, 60L * 20L);
    }

    public void cancel() {
        if (!expirationTask.isCancelled()) {
            expirationTask.cancel();
        }
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }
}