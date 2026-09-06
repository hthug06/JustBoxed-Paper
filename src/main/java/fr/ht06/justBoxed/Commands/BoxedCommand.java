package fr.ht06.justBoxed.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.ht06.justBoxed.Box.Box;
import fr.ht06.justBoxed.Box.BoxService;
import fr.ht06.justBoxed.JustBoxed;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
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
                .then(Commands.literal("accept")
                        .requires(this::requireBoxlessPlayer)
                        .then(Commands.argument("box_uuid", StringArgumentType.greedyString())
                                .executes(this::acceptInvitation)
                        )
                )
                .then(Commands.literal("create")
                        .requires(this::requireBoxlessPlayer)
                        .then(Commands.argument("box_name", StringArgumentType.greedyString())
                                .executes(this::createBox)
                        )
                )
                .then(Commands.literal("delete")
                        .requires(this::requireOwner)
                        .executes(this::deleteBox))
                .then(Commands.literal("deny")
                        .requires(this::requireBoxlessPlayer)
                        .then(Commands.argument("box_uuid", StringArgumentType.greedyString())
                                .executes(this::denyInvitation)
                        )
                )
                .then(Commands.literal("invite")
                        .requires(this::requireOwner)
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(this::invitePlayer)
                        )
                )
                .then(Commands.literal("kick")
                        .requires(this::requireOwner)
                        .then(Commands.argument("target", StringArgumentType.word())
                                // Suggest every member of the box
                                .suggests((ctx, builder) -> {
                                    if (ctx.getSource().getSender() instanceof Player player
                                            && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()) != null) {
                                        Box box = JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId());
                                        for (UUID member : box.getMembers()) {
                                            builder.suggest(Bukkit.getOfflinePlayer(member).getName());
                                        }

                                    }

                                    return builder.buildFuture();
                                })
                                .executes(this::kickPlayerFromBox)
                                .then(Commands.literal("confirm")
                                        .executes(this::kickConfirmPlayerFromBox)
                                )
                        )
                )
                .then(Commands.literal("leave")
                        .requires(this::requireNotOwner)
                        .executes(this::leaveBox)
                        .then(Commands.literal("confirm")
                                .executes(this::leaveBoxConfirm)
                        )
                )
                .then(Commands.literal("setname")
                        .requires(this::requireOwner)
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
        return requirePlayerWithBox(source)
                && source.getSender() instanceof Player player
                && JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()).getOwner().equals(player.getUniqueId());
    }

    private boolean requireNotOwner(CommandSourceStack source) {
        return requirePlayerWithBox(source)
                && source.getSender() instanceof Player player
                && !JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId()).getOwner().equals(player.getUniqueId());
    }

    private int acceptInvitation(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can join a box!");
            return Command.SINGLE_SUCCESS;
        }

        UUID boxUuid;
        try {
             boxUuid = UUID.fromString(StringArgumentType.getString(ctx, "box_uuid"));
        } catch (IllegalArgumentException e) {
            player.sendPlainMessage("Invalid box UUID format!");
            return Command.SINGLE_SUCCESS;
        }

        Box box = this.plugin.getBoxRegistry().getBoxByUuid(boxUuid);

        if (box == null) {
            player.sendPlainMessage("This box does not exist!");
            return Command.SINGLE_SUCCESS;
        }

        this.boxService.acceptInvitation(box, player.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendPlainMessage("Invitation accepted ! Teleporting...");
                    } else {
                        player.sendPlainMessage("This invitation is no longer valid or has expired.");
                    }
                })
                .exceptionally(ex -> {
                    this.plugin.getLogger().severe("Error accepting the box : " + ex.getMessage());
                    player.sendPlainMessage("An error occurred during registration.");
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private int createBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can create a box!");
            return Command.SINGLE_SUCCESS;
        }
        String rawName = StringArgumentType.getString(ctx, "box_name");

        this.boxService.createBox(rawName, player).thenAccept(_ -> player.sendPlainMessage("Box created ! Teleporting...")).exceptionally(ex -> {
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

    private int denyInvitation(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can deny an invitation to a box!");
            return Command.SINGLE_SUCCESS;
        }
        String rawUuid = StringArgumentType.getString(ctx, "box_uuid");
        UUID boxUuid = UUID.fromString(rawUuid);
        Box box = this.plugin.getBoxRegistry().getBoxByUuid(boxUuid);

        if (box == null) {
            player.sendPlainMessage("This box does not exist!");
            return Command.SINGLE_SUCCESS;
        }

        if (this.boxService.denyInvite(box, player.getUniqueId())) {
            player.sendMessage(Component.text("You deny the invitation of ").append(box.getDisplayName()));

            OfflinePlayer target = Bukkit.getOfflinePlayer(player.getUniqueId());
            String targetName = target.getName() != null ? target.getName() : "A player";
            box.sendMessageToOwner(Component.text(targetName + " has denied the invitation !"));
        } else {
            player.sendPlainMessage("You are not invited to this box!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int invitePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can invite another player into their box!");
            return Command.SINGLE_SUCCESS;
        }

        final PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        final List<Player> players = resolver.resolve(ctx.getSource());

        if (players.isEmpty()) {
            ctx.getSource().getSender().sendPlainMessage("no player found");
            return Command.SINGLE_SUCCESS;
        }
        final Player target = players.getFirst();

        // Check if the player already have a box
        if (JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(target.getUniqueId()) != null) {
            player.sendPlainMessage("This player already have a box");
            return Command.SINGLE_SUCCESS;
        } else {
            Box box = JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId());
            if (this.boxService.invitePlayer(plugin, box, player.getUniqueId(), target.getUniqueId())) {

                // Send the message to the invited player

                // Accept button
                Component acceptButton = Component.text("Accept", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/box accept " + box.getUuid()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to accept the invitation", NamedTextColor.GREEN)));

                // Deny button
                Component denyButton = Component.text("Deny", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/box deny " + box.getUuid()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to deny the invitation", NamedTextColor.RED)));

                // Inviter component
                Component inviterComponent = player.displayName()
                        .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "));

                // Assemble
                Component inviteMessage = inviterComponent
                        .append(Component.text(" invited you to join his box "))
                        .append(box.getDisplayName())
                        .append(Component.text(" !"))
                        .append(Component.newline())

                        // Actions
                        .append(Component.text("  "))
                        .append(acceptButton)
                        .append(Component.text("   "))
                        .append(denyButton)
                        .append(Component.text("  (Expire in 60s)", NamedTextColor.DARK_GRAY));

                target.sendMessage(inviteMessage);
                box.sendMessageToOwner(Component.text("Player invited!"));

            } else {
                box.sendMessageToOwner(Component.text("Player already invited."));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private int kickPlayerFromBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can change kick a player out of a box!");
            return Command.SINGLE_SUCCESS;
        }
        OfflinePlayer kickedPlayer = Bukkit.getOfflinePlayer(ctx.getArgument("target", String.class));

        // Prepare message
        Component message = Component.text("Do you really want to kick " + kickedPlayer.getName() + " out of this box?")
                .appendNewline()
                .append(Component.text("KICK IT", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/box kick " + kickedPlayer.getName() + " confirm"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to kick the player", NamedTextColor.RED))));

        player.sendMessage(message);

        return Command.SINGLE_SUCCESS;

    }

    private int kickConfirmPlayerFromBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can change kick a player out of a box!");
            return Command.SINGLE_SUCCESS;
        }
        Box box = JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId());
        OfflinePlayer kickedPlayer = Bukkit.getOfflinePlayer(ctx.getArgument("target", String.class));

        // remove member from registry and database
        this.boxService.removeMember(box, kickedPlayer.getUniqueId())
                // Maybe delete his inventory (later)
                .thenAccept(_ -> {
                    if (kickedPlayer.isOnline()) {
                        Player kickedPlayerOnline = kickedPlayer.getPlayer();
                        if (kickedPlayerOnline != null) {
                            kickedPlayerOnline.sendMessage(Component.text("You have been kicked from ").append(box.getDisplayName()));
                            kickedPlayerOnline.teleport(Bukkit.getWorld("world").getSpawnLocation());
                        }

                    }
                })
                .exceptionally(ex -> {
                    this.plugin.getLogger().severe("Error when kicking a player: " + ex.getMessage());
                    player.sendPlainMessage("Failed to kick a player (please contact an administrator)");
                    return null;
                });
        box.broadcastMessage(kickedPlayer.getName() + " has been kicked from the box");


        return Command.SINGLE_SUCCESS;
    }

    private int leaveBox(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can change the team name of a box!");
            return Command.SINGLE_SUCCESS;
        }

        // Prepare message
        Component message = Component.text("Do you really want to leave this box?")
                .appendNewline()
                .append(Component.text("LEAVE IT", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/box leave confirm"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to kick the player", NamedTextColor.RED))));

        player.sendMessage(message);

        return Command.SINGLE_SUCCESS;
    }

    private int leaveBoxConfirm(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendPlainMessage("Only players can change the team name of a box!");
            return Command.SINGLE_SUCCESS;
        }

        Box box = JustBoxed.getInstance().getBoxRegistry().getBoxByPlayer(player.getUniqueId());

        this.boxService.removeMember(box, player.getUniqueId())
                .thenAccept(_ -> {
                    player.teleportAsync(Bukkit.getWorld("world").getSpawnLocation());
                    player.sendMessage(Component.text("You successfully leave ").append(box.getDisplayName()));
                })
                .exceptionally(ex -> {
                    this.plugin.getLogger().severe("Error when leaving a box: " + ex.getMessage());
                    player.sendPlainMessage("Failed to leave the box (please contact an administrator)");
                    return null;
                });
        box.broadcastMessage(player.getName() +  "leaved the box...");

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
        player.sendMessage(Component.text("Owner : " + Bukkit.getOfflinePlayer(box.getOwner()).getName()));
        if (!box.getMembers().isEmpty()) {
            player.sendMessage(Component.text("Members : "));
            for (UUID member : box.getMembers()) {
                player.sendMessage(Component.text("- " + Bukkit.getOfflinePlayer(member).getName()));
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
        } else {
            player.sendPlainMessage("Loading world...");
            World world = box.loadWorld(this.plugin);
            player.teleportAsync(world.getSpawnLocation().toCenterLocation());
            player.sendPlainMessage("Teleported to box !");
        }
        return Command.SINGLE_SUCCESS;
    }
}
