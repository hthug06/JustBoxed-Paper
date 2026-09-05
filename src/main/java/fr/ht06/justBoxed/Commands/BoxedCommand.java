package fr.ht06.justBoxed.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.ht06.justBoxed.Box.Box;
import fr.ht06.justBoxed.Box.BoxService;
import fr.ht06.justBoxed.JustBoxed;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BoxedCommand {
    private final JustBoxed plugin;
    private final BoxService boxService;

    public BoxedCommand(JustBoxed plugin, BoxService boxService) {
        this.plugin = plugin;
        this.boxService = boxService;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("box")
                .then(Commands.literal("create")
                        .requires(this::requireBoxlessPlayer)
                        .then(Commands.argument("box_name", StringArgumentType.greedyString())
                                .executes(this::createBox)
                        )
                )
                .then(Commands.literal("delete")
                        .requires(source -> this.requirePlayerWithBox(source) && this.requireOwner(source))
                        .executes(this::deleteBox))
                .then(Commands.literal("setname")
                        .requires(source -> this.requirePlayerWithBox(source) && this.requireOwner(source))
                        .then(Commands.argument("box_name", StringArgumentType.greedyString())
                                .executes(this::setBoxName)
                        )
                )
                .then(Commands.literal("team")
                        .requires(this::requirePlayerWithBox)
                        .executes(this::teamBox))
                .then(Commands.literal("teleport")
                        .requires(this::requirePlayerWithBox)
                        .executes(this::teleportToBox))
                ;
    }

    private boolean requireBoxlessPlayer(CommandSourceStack source) {
        return source.getSender() instanceof Player player
                && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()) == null;
    }

    private boolean requirePlayerWithBox(CommandSourceStack source) {
        return source.getSender() instanceof Player player
                && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()) != null;
    }

    private boolean requireOwner(CommandSourceStack source) {
        return source.getSender() instanceof Player player
                && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()).getOwner().equals(player.getUniqueId());
    }

    private int createBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can create a box!");
            return Command.SINGLE_SUCCESS;
        }
        String rawName = StringArgumentType.getString(ctx, "box_name");

        this.boxService.createBox(rawName, player).thenAccept(box -> {
            player.sendPlainMessage("Box created ! Teleporting...");
        }).exceptionally(ex -> {
            this.plugin.getLogger().severe("Error when creating box : " + ex.getMessage());
            player.sendPlainMessage("Failed to create a box (please contact an administrator)");
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    private int deleteBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can delete a box!");
            return Command.SINGLE_SUCCESS;
        }

        // Check for box ownership is done in the brigadier
        this.boxService.deleteBox(JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId())).thenAccept(_ -> player.sendPlainMessage("Box deleted !")).exceptionally(ex -> {
            this.plugin.getLogger().severe("Error when deleting box : " + ex.getMessage());
            player.sendPlainMessage("Failed to delete box (please contact an administrator)");
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    private int setBoxName(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can change the team name of a box!");
            return Command.SINGLE_SUCCESS;
        }

        Box box = JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId());
        this.boxService.setBoxName(box, ctx.getArgument("box_name", String.class))
                .thenAccept(_ -> player.sendMessage(Component.text("Box name changed : ").append(box.getDisplayName())))
                .exceptionally(ex -> {
                    this.plugin.getLogger().severe("Error when changing team name : " + ex.getMessage());
                    player.sendPlainMessage("Failed to change team name (please contact an administrator)");
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }


    private int teamBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can see their team!");
            return Command.SINGLE_SUCCESS;
        }

        Box box = JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId());

        // Display is like:
        // --- [team display name] ---
        // Owner : [owner username]
        // Members : (if there are members)
        // - [member username]
        // - [member username]

        player.sendMessage(Component.text("--- ").append(box.getDisplayName()).append(Component.text(" ---")));
        player.sendMessage(Component.text("Owner : ").append(Bukkit.getPlayer(box.getOwner()).displayName()));
        if (!box.getMembers().isEmpty()) {
            player.sendMessage(Component.text("Members : "));
            for (UUID member : box.getMembers()) {
                player.sendMessage(Component.text("- ").append(Bukkit.getPlayer(member).displayName()));
            }
        }


        return Command.SINGLE_SUCCESS;
    }

    private int teleportToBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can teleport to a box!");
            return Command.SINGLE_SUCCESS;
        }

        Box box = JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId());
        if (box.isWorldLoaded(this.plugin)) {
            player.teleportAsync(box.getWorld(this.plugin).getSpawnLocation().toCenterLocation()).thenRun(() -> player.sendPlainMessage("Teleported to box !"));
        }
        else {
            player.sendPlainMessage("Loading world...");
            World world = box.loadWorld(this.plugin);
            player.teleportAsync(world.getSpawnLocation().toCenterLocation());
            player.sendPlainMessage("Teleported to box !");
        }
        return Command.SINGLE_SUCCESS;
    }
}
