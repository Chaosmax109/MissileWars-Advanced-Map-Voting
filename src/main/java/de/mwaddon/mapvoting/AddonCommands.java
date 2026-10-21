package de.mwaddon.mapvoting;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import de.butzlabben.missilewars.configuration.PluginMessages;
import de.butzlabben.missilewars.configuration.arena.ArenaConfig;
import de.butzlabben.missilewars.game.Arenas;
import de.butzlabben.missilewars.game.Game;
import de.butzlabben.missilewars.game.GameManager;
import de.butzlabben.missilewars.game.MapVoting;
import de.butzlabben.missilewars.game.enums.MapChooseProcedure;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Additional sub commands for /mw (registered in the command manager of MissileWars).
 */
@CommandAlias("mw|missilewars")
public class AddonCommands extends BaseCommand {

    private final MapVotingAddon addon;

    public AddonCommands(MapVotingAddon addon) {
        this.addon = addon;
    }

    @Subcommand("forcemap|setmap")
    @CommandCompletion("@arenas @games @nothing")
    @CommandPermission("mw.forcemap")
    @Description("Force the map of a game.")
    public void forcemapCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(addon.getMessages().get("forcemap-usage"));
            return;
        }
        if (args.length > 2) {
            sender.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.COMMAND_TO_MANY_ARGUMENTS));
            return;
        }

        Game game;
        if (args.length == 2) {
            game = GameManager.getInstance().getGame(args[1]);
            if (game == null) {
                sender.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.COMMAND_INVALID_GAME)
                        .replace("%input%", args[1]));
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(addon.getMessages().get("forcemap-usage"));
                return;
            }
            game = GameManager.getInstance().getGame(((Player) sender).getLocation());
            if (game == null) {
                sender.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.GAME_NOT_IN_GAME_AREA));
                return;
            }
        }

        AddonMapVoting voting = getVoting(sender, game);
        if (voting == null) return;

        ArenaConfig arenaConfig = voting.findArena(args[0]);
        if (arenaConfig == null) {
            if (Arenas.getFromName(args[0]) != null) {
                sender.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.VOTE_MAP_NOT_AVAILABLE));
            } else {
                sender.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.COMMAND_INVALID_MAP)
                        .replace("%input%", args[0]));
            }
            return;
        }

        voting.forceArena(sender, arenaConfig);
    }

    @Subcommand("unvote|removevote")
    @CommandCompletion("@arenas @nothing")
    @CommandPermission("mw.vote")
    @Description("Remove a vote (or all votes without map).")
    public void unvoteCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.COMMAND_ONLY_PLAYERS));
            return;
        }
        Player player = (Player) sender;
        if (args.length > 1) {
            player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.COMMAND_TO_MANY_ARGUMENTS));
            return;
        }

        Game game = GameManager.getInstance().getGame(player.getLocation());
        if (game == null) {
            player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.GAME_NOT_IN_GAME_AREA));
            return;
        }
        if (game.getGameConfig().getMapChooseProcedure() != MapChooseProcedure.MAPVOTING) {
            player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.VOTE_CANT_VOTE));
            return;
        }

        AddonMapVoting voting = getVoting(sender, game);
        if (voting == null) return;

        if (args.length == 0) {
            voting.removeAllVotes(player);
        } else {
            voting.removeVote(player, args[0]);
        }
    }

    @Subcommand("voteall")
    @CommandCompletion("@arenas @nothing")
    @CommandPermission("mw.vote")
    @Description("Give all remaining votes to a map.")
    public void voteallCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.COMMAND_ONLY_PLAYERS));
            return;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.COMMAND_MAP_NEEDED));
            return;
        }
        if (args.length > 1) {
            player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.COMMAND_TO_MANY_ARGUMENTS));
            return;
        }

        Game game = GameManager.getInstance().getGame(player.getLocation());
        if (game == null) {
            player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.GAME_NOT_IN_GAME_AREA));
            return;
        }
        if (game.getGameConfig().getMapChooseProcedure() != MapChooseProcedure.MAPVOTING) {
            player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.VOTE_CANT_VOTE));
            return;
        }

        AddonMapVoting voting = getVoting(sender, game);
        if (voting == null) return;
        voting.addAllVotes(player, args[0]);
    }

    @Subcommand("clearvotes|resetvotes")
    @CommandCompletion("@nothing")
    @CommandPermission("mw.vote")
    @Description("Remove all own votes.")
    public void clearvotesCommand(CommandSender sender, String[] args) {
        unvoteCommand(sender, new String[0]);
    }

    @Subcommand("mapvotingreload")
    @CommandCompletion("@nothing")
    @CommandPermission("mw.reload")
    @Description("Reload the config of the map voting addon.")
    public void reloadCommand(CommandSender sender, String[] args) {
        addon.reloadAddon();
        sender.sendMessage(addon.getMessages().get("reload"));
    }

    private AddonMapVoting getVoting(CommandSender sender, Game game) {
        MapVoting voting = game.getMapVoting();
        if (voting instanceof AddonMapVoting) return (AddonMapVoting) voting;
        // The game was just created and not yet patched (max. 1 tick).
        sender.sendMessage(addon.getMessages().get("forcemap-failed"));
        return null;
    }
}
