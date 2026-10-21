package de.mwaddon.mapvoting;

import de.butzlabben.missilewars.MissileWars;
import de.butzlabben.missilewars.game.Game;
import de.butzlabben.missilewars.game.GameManager;
import de.butzlabben.missilewars.game.MapVoting;
import de.butzlabben.missilewars.menus.inventory.MapVoteMenu;
import de.butzlabben.missilewars.player.MWPlayer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * Addon for MissileWars:
 * - multiple votes per player (mw.vote.amount.<n>), stacking (mw.vote.stack)
 * - /mw unvote [map]
 * - /mw forcemap <map> [game]
 *
 * The addon replaces the MapVoting object of every MissileWars game and the
 * map vote menu of every player with its own implementation at runtime.
 */
public class MapVotingAddon extends JavaPlugin {

    private static MapVotingAddon instance;

    private Messages messages;
    private VoteGuiManager guiManager;
    private AddonCommands commands;
    private BukkitTask injectTask;

    private Field gameMapVotingField;
    private Field playerMapVoteMenuField;
    private boolean injectErrorLogged = false;

    @Override
    public void onEnable() {
        instance = this;

        if (Bukkit.getPluginManager().getPlugin("MissileWars") == null) {
            getLogger().severe("MissileWars was not found. The addon will be disabled.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        messages = new Messages(this);

        try {
            gameMapVotingField = Game.class.getDeclaredField("mapVoting");
            gameMapVotingField.setAccessible(true);
            playerMapVoteMenuField = MWPlayer.class.getDeclaredField("mapVoteMenu");
            playerMapVoteMenuField.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            getLogger().log(Level.SEVERE, "This MissileWars version is not supported by the addon.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        guiManager = new VoteGuiManager(this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);

        commands = new AddonCommands(this);
        MissileWars.getInstance().getCommandManager().registerCommand(commands);

        // New games (after every round) and new players are detected and patched every tick.
        injectTask = Bukkit.getScheduler().runTaskTimer(this, this::injectAll, 1L, 1L);

        getLogger().info("MissileWars MapVoting addon enabled.");
    }

    @Override
    public void onDisable() {
        if (injectTask != null) injectTask.cancel();
        if (guiManager != null) guiManager.closeAll();
        if (commands != null) {
            try {
                MissileWars.getInstance().getCommandManager().unregisterCommand(commands);
            } catch (Throwable ignored) {
            }
        }
        instance = null;
    }

    /**
     * Replaces the original voting and menu objects of all games and players.
     */
    private void injectAll() {
        try {
            for (Game game : new ArrayList<>(GameManager.getInstance().getGames().values())) {
                MapVoting current = game.getMapVoting();
                if (!(current instanceof AddonMapVoting)) {
                    gameMapVotingField.set(game, new AddonMapVoting(this, game, current.getState()));
                }

                for (MWPlayer mwPlayer : new ArrayList<>(game.getPlayers().values())) {
                    MapVoteMenu menu = mwPlayer.getMapVoteMenu();
                    if (!(menu instanceof AddonVoteMenu)) {
                        playerMapVoteMenuField.set(mwPlayer, new AddonVoteMenu(this, mwPlayer));
                    }
                }
            }
        } catch (Exception e) {
            if (!injectErrorLogged) {
                injectErrorLogged = true;
                getLogger().log(Level.SEVERE, "Could not inject the map voting into MissileWars.", e);
            }
        }
    }

    public void reloadAddon() {
        reloadConfig();
        messages = new Messages(this);
    }

    public static MapVotingAddon getInstance() {
        return instance;
    }

    public Messages getMessages() {
        return messages;
    }

    public VoteGuiManager getGuiManager() {
        return guiManager;
    }

    public int getMaxVoteLimit() {
        return Math.max(1, getConfig().getInt("max-votes-limit", 100));
    }
}
