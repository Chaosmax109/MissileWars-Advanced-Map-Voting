package de.mwaddon.mapvoting;

import de.butzlabben.missilewars.configuration.PluginMessages;
import de.butzlabben.missilewars.configuration.arena.ArenaConfig;
import de.butzlabben.missilewars.game.Arenas;
import de.butzlabben.missilewars.game.Game;
import de.butzlabben.missilewars.game.GameWorld;
import de.butzlabben.missilewars.game.MapVoting;
import de.butzlabben.missilewars.game.enums.GameState;
import de.butzlabben.missilewars.game.enums.MapChooseProcedure;
import de.butzlabben.missilewars.game.enums.VoteState;
import de.butzlabben.missilewars.player.MWPlayer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Replacement for the MissileWars MapVoting with multiple votes per player.
 */
public class AddonMapVoting extends MapVoting {

    /** Permission prefix for the amount of votes: mw.vote.amount.<number> */
    public static final String PERM_VOTE_AMOUNT = "mw.vote.amount.";
    /** Permission to put more than one vote on the same map. */
    public static final String PERM_VOTE_STACK = "mw.vote.stack";
    /** Upper limit to protect against typos like mw.vote.amount.1000000 */

    /** player UUID -> (arena name -> amount of votes on this arena) */
    private final Map<UUID, Map<String, Integer>> playerVotes = new HashMap<>();
    private final Game game;
    private VoteState state = VoteState.NULL;
    private boolean forced = false;

    private final MapVotingAddon addon;

    public AddonMapVoting(MapVotingAddon addon, Game game, VoteState initialState) {
        super(game);
        this.addon = addon;
        this.game = game;
        if (initialState != null) this.state = initialState;
    }

    private Messages msg() {
        return addon.getMessages();
    }

    private void refreshMenus() {
        addon.getGuiManager().refresh(game);
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    /**
     * Returns the amount of votes the player can give. Default is 1.
     * The highest "mw.vote.amount.<n>" permission of the player is used.
     */
    public static int getMaxVotes(Player player) {
        int max = 1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission().toLowerCase();
            if (!perm.startsWith(PERM_VOTE_AMOUNT)) continue;
            try {
                int amount = Integer.parseInt(perm.substring(PERM_VOTE_AMOUNT.length()));
                if (amount > max) max = amount;
            } catch (NumberFormatException ignored) {
            }
        }
        MapVotingAddon addon = MapVotingAddon.getInstance();
        return addon == null ? max : Math.min(max, addon.getMaxVoteLimit());
    }

    public static boolean canStackVotes(Player player) {
        return player.hasPermission(PERM_VOTE_STACK);
    }

    // ------------------------------------------------------------------
    // Voting
    // ------------------------------------------------------------------

    private boolean checkVoteState(Player player) {
        if (state == VoteState.NULL) {
            player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.VOTE_CHANGE_TEAM_NOT_NOW));
            return false;
        }
        if (state == VoteState.FINISH) {
            player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.VOTE_CHANGE_TEAM_NO_LONGER_NOW));
            return false;
        }
        return true;
    }

    private ArenaConfig getVoteableArena(Player player, String arenaName) {
        ArenaConfig arenaConfig = findArena(arenaName);
        if (arenaConfig == null) {
            if (Arenas.getFromName(arenaName) != null) {
                player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.VOTE_MAP_NOT_AVAILABLE));
            } else {
                player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.COMMAND_INVALID_MAP)
                        .replace("%input%", arenaName));
            }
        }
        return arenaConfig;
    }

    /**
     * Gives one vote of the player to the specified arena.
     */
    @Override
    public void addVote(Player player, String arenaName) {
        if (!checkVoteState(player)) return;

        ArenaConfig arenaConfig = getVoteableArena(player, arenaName);
        if (arenaConfig == null) return;

        UUID uuid = player.getUniqueId();
        Map<String, Integer> votes = playerVotes.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
        int maxVotes = getMaxVotes(player);
        int usedVotes = getUsedVotes(uuid);
        int votesOnArena = votes.getOrDefault(arenaConfig.getName(), 0);

        if (maxVotes <= 1) {
            // Classic behaviour: one vote that is moved to the new map.
            if (votesOnArena > 0) {
                player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.VOTE_ARENA_ALREADY_SELECTED)
                        .replace("%map%", arenaConfig.getDisplayName()));
                return;
            }
            votes.clear();
            votes.put(arenaConfig.getName(), 1);
            player.sendMessage(PluginMessages.getMessage(true, PluginMessages.MessageEnum.VOTE_SUCCESS)
                    .replace("%map%", arenaConfig.getDisplayName()));
            refreshMenus();
            return;
        }

        if (votesOnArena > 0 && !canStackVotes(player)) {
            player.sendMessage(msg().get("vote-no-stack")
                    .replace("%map%", arenaConfig.getDisplayName()));
            return;
        }

        if (usedVotes >= maxVotes) {
            player.sendMessage(msg().get("vote-no-votes-left")
                    .replace("%map%", arenaConfig.getDisplayName())
                    .replace("%used%", Integer.toString(usedVotes))
                    .replace("%max%", Integer.toString(maxVotes)));
            return;
        }

        votes.put(arenaConfig.getName(), votesOnArena + 1);
        player.sendMessage(msg().get("vote-success")
                .replace("%map%", arenaConfig.getDisplayName())
                .replace("%votes_map%", Integer.toString(votesOnArena + 1))
                .replace("%used%", Integer.toString(usedVotes + 1))
                .replace("%max%", Integer.toString(maxVotes)));
        refreshMenus();
    }

    /**
     * Gives all remaining votes of the player to the specified arena.
     * Without the stack permission (or with only one vote) this is a normal vote.
     */
    public void addAllVotes(Player player, String arenaName) {
        int maxVotes = getMaxVotes(player);
        if (maxVotes <= 1 || !canStackVotes(player)) {
            addVote(player, arenaName);
            return;
        }

        if (!checkVoteState(player)) return;

        ArenaConfig arenaConfig = getVoteableArena(player, arenaName);
        if (arenaConfig == null) return;

        UUID uuid = player.getUniqueId();
        Map<String, Integer> votes = playerVotes.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
        int usedVotes = getUsedVotes(uuid);
        int remaining = maxVotes - usedVotes;

        if (remaining <= 0) {
            player.sendMessage(msg().get("vote-no-votes-left")
                    .replace("%map%", arenaConfig.getDisplayName())
                    .replace("%used%", Integer.toString(usedVotes))
                    .replace("%max%", Integer.toString(maxVotes)));
            return;
        }

        int votesOnArena = votes.getOrDefault(arenaConfig.getName(), 0) + remaining;
        votes.put(arenaConfig.getName(), votesOnArena);
        player.sendMessage(msg().get("vote-success-all")
                .replace("%map%", arenaConfig.getDisplayName())
                .replace("%amount%", Integer.toString(remaining))
                .replace("%votes_map%", Integer.toString(votesOnArena))
                .replace("%used%", Integer.toString(maxVotes))
                .replace("%max%", Integer.toString(maxVotes)));
        refreshMenus();
    }

    /**
     * Removes one vote of the player from the specified arena.
     */
    public void removeVote(Player player, String arenaName) {
        if (!checkVoteState(player)) return;

        ArenaConfig arenaConfig = getVoteableArena(player, arenaName);
        if (arenaConfig == null) return;

        UUID uuid = player.getUniqueId();
        Map<String, Integer> votes = playerVotes.get(uuid);
        int votesOnArena = votes == null ? 0 : votes.getOrDefault(arenaConfig.getName(), 0);

        if (votesOnArena <= 0) {
            player.sendMessage(msg().get("vote-not-voted")
                    .replace("%map%", arenaConfig.getDisplayName()));
            return;
        }

        if (votesOnArena == 1) {
            votes.remove(arenaConfig.getName());
        } else {
            votes.put(arenaConfig.getName(), votesOnArena - 1);
        }

        player.sendMessage(msg().get("vote-removed")
                .replace("%map%", arenaConfig.getDisplayName())
                .replace("%votes_map%", Integer.toString(votesOnArena - 1))
                .replace("%used%", Integer.toString(getUsedVotes(uuid)))
                .replace("%max%", Integer.toString(getMaxVotes(player))));
        refreshMenus();
    }

    /**
     * Removes all votes of the player.
     */
    public void removeAllVotes(Player player) {
        if (!checkVoteState(player)) return;

        Map<String, Integer> votes = playerVotes.remove(player.getUniqueId());
        if (votes == null || votes.isEmpty()) {
            player.sendMessage(msg().get("vote-no-votes-given"));
            return;
        }
        player.sendMessage(msg().get("vote-removed-all"));
        refreshMenus();
    }

    // ------------------------------------------------------------------
    // Results
    // ------------------------------------------------------------------

    public int getUsedVotes(UUID uuid) {
        Map<String, Integer> votes = playerVotes.get(uuid);
        if (votes == null) return 0;
        int sum = 0;
        for (int v : votes.values()) sum += v;
        return sum;
    }

    public int getVotesOfPlayer(ArenaConfig arenaConfig, MWPlayer mwPlayer) {
        Map<String, Integer> votes = playerVotes.get(mwPlayer.getPlayer().getUniqueId());
        if (votes == null) return 0;
        return votes.getOrDefault(arenaConfig.getName(), 0);
    }

    public int getVotesOf(ArenaConfig arenaConfig) {
        int sum = 0;
        for (Map<String, Integer> votes : playerVotes.values()) {
            sum += votes.getOrDefault(arenaConfig.getName(), 0);
        }
        return sum;
    }

    public int getTotalVotes() {
        int sum = 0;
        for (Map<String, Integer> votes : playerVotes.values()) {
            for (int v : votes.values()) sum += v;
        }
        return sum;
    }

    private ArenaConfig getVotedArena() {
        List<ArenaConfig> arenas = game.getGameConfig().getArenas();
        if (getTotalVotes() == 0) return arenas.get(0);

        int highest = -1;
        List<ArenaConfig> best = new ArrayList<>();
        for (ArenaConfig arena : arenas) {
            int votes = getVotesOf(arena);
            if (votes > highest) {
                highest = votes;
                best.clear();
                best.add(arena);
            } else if (votes == highest) {
                best.add(arena);
            }
        }
        // On a tie a random map of the best ones is selected.
        return best.get(ThreadLocalRandom.current().nextInt(best.size()));
    }

    @Override
    public double getPercentOf(ArenaConfig arenaConfig) {
        int total = getTotalVotes();
        if (total == 0) return 0;
        return (double) getVotesOf(arenaConfig) / total * 100;
    }

    @Override
    public String getPercentOfMsg(ArenaConfig arenaConfig) {
        double result = Math.round(getPercentOf(arenaConfig));
        return Double.toString(result);
    }

    @Override
    public boolean isVotedMapOfPlayer(ArenaConfig arenaConfig, MWPlayer mwPlayer) {
        return getVotesOfPlayer(arenaConfig, mwPlayer) > 0;
    }

    // ------------------------------------------------------------------
    // Vote lifecycle
    // ------------------------------------------------------------------

    @Override
    public void startVote() {
        if (game.getGameConfig().getMapChooseProcedure() != MapChooseProcedure.MAPVOTING)
            throw new IllegalStateException("Defined map choose procedure is not \"MAPVOTING\"");
        state = VoteState.RUNNING;
    }

    @Override
    public void stopVote() {
        if (game.getGameConfig().getMapChooseProcedure() != MapChooseProcedure.MAPVOTING)
            throw new IllegalStateException("Defined map choose procedure is not \"MAPVOTING\"");
        state = VoteState.FINISH;
    }

    @Override
    public boolean onlyOneArenaFound() {
        return game.getGameConfig().getArenas().size() == 1;
    }

    @Override
    public void setVotedArena() {
        if (game.getGameConfig().getMapChooseProcedure() != MapChooseProcedure.MAPVOTING)
            throw new IllegalStateException("Defined map choose procedure is not \"MAPVOTING\"");

        if (onlyOneArenaFound()) return;
        if (state != VoteState.RUNNING) return;

        stopVote();

        ArenaConfig arenaConfig = getVotedArena();
        if (arenaConfig == null) throw new IllegalStateException("Voted arena is not present");

        game.setArena(arenaConfig);
        game.broadcast(PluginMessages.getMessage(true, PluginMessages.MessageEnum.VOTE_FINISHED)
                .replace("%map%", game.getArenaConfig().getDisplayName()));
        game.prepareGame();
        addon.getGuiManager().closeAll(game);
    }

    // ------------------------------------------------------------------
    // Force map
    // ------------------------------------------------------------------

    /**
     * Searches the arena in the arena list of this game. Accepts the
     * arena name (case-insensitive) or the display name (without colors).
     */
    public ArenaConfig findArena(String input) {
        if (input == null) return null;
        List<ArenaConfig> arenas = game.getGameConfig().getArenas();
        for (ArenaConfig arena : arenas) {
            if (arena.getName().equals(input)) return arena;
        }
        for (ArenaConfig arena : arenas) {
            if (arena.getName().equalsIgnoreCase(input)) return arena;
        }
        String strippedInput = strip(input);
        for (ArenaConfig arena : arenas) {
            if (arena.getDisplayName() != null && strip(arena.getDisplayName()).equalsIgnoreCase(strippedInput))
                return arena;
        }
        return null;
    }

    private static String strip(String text) {
        String stripped = ChatColor.stripColor(PluginMessages.getConvertedMsg(text));
        return stripped == null ? "" : stripped.trim();
    }

    /**
     * Forces the specified arena for this game. Only possible in lobby state.
     *
     * @return true, if the arena was set
     */
    public boolean forceArena(CommandSender sender, ArenaConfig arenaConfig) {
        if (game.getState() != GameState.LOBBY) {
            sender.sendMessage(msg().get("forcemap-not-in-lobby"));
            return false;
        }

        ArenaConfig current = game.getArenaConfig();
        if (current != null && current.getName().equals(arenaConfig.getName())) {
            sender.sendMessage(msg().get("forcemap-already-selected")
                    .replace("%map%", arenaConfig.getDisplayName()));
            return false;
        }

        try {
            if (current != null) {
                unloadCurrentArena();
            }

            if (game.getGameConfig().getMapChooseProcedure() == MapChooseProcedure.MAPVOTING) {
                state = VoteState.FINISH;
            }
            forced = true;

            game.setArena(arenaConfig);
            if (game.getArenaConfig() == null) throw new IllegalStateException("Arena could not be set");
            game.prepareGame();

        } catch (Exception e) {
            de.butzlabben.missilewars.Logger.ERROR.log("Could not force arena \"" + arenaConfig.getName()
                    + "\" for game \"" + game.getGameConfig().getName() + "\": " + e);
            e.printStackTrace();
            sender.sendMessage(msg().get("forcemap-failed"));
            return false;
        }

        addon.getGuiManager().closeAll(game);
        game.broadcast(msg().get("forcemap-broadcast")
                .replace("%map%", game.getArenaConfig().getDisplayName()));
        sender.sendMessage(msg().get("forcemap-success")
                .replace("%map%", game.getArenaConfig().getDisplayName())
                .replace("%game%", game.getGameConfig().getDisplayName()));
        return true;
    }

    /**
     * Unloads the already selected arena of the game (lobby state only),
     * so that a new arena can be set.
     */
    private void unloadCurrentArena() throws ReflectiveOperationException {
        GameWorld gameWorld = game.getGameWorld();
        if (gameWorld != null) {
            org.bukkit.World world = gameWorld.getWorld();
            if (world != null) {
                for (Player player : world.getPlayers()) {
                    game.teleportToLobbySpawn(player);
                }
            }
            gameWorld.unload();
            gameWorld.delete();
        }
        game.getPortalBlocks().clear();

        setGameField("arenaConfig", null);
        setGameField("gameWorld", null);
        setGameField("gameArea", null);
        setGameField("innerGameArea", null);
        setGameField("ready", false);

        // prepareGame() counts the fights for the "fight restart" option.
        // The fight was already counted for the old arena.
        Field fights = Game.class.getDeclaredField("fights");
        fights.setAccessible(true);
        fights.setInt(null, Math.max(0, fights.getInt(null) - 1));
    }

    private void setGameField(String name, Object value) throws ReflectiveOperationException {
        Field field = Game.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(game, value);
    }

    public boolean isForced() {
        return forced;
    }

    @Override
    public VoteState getState() {
        return state;
    }
}
