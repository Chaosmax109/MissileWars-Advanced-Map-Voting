package de.mwaddon.mapvoting;

import de.butzlabben.missilewars.menus.inventory.MapVoteMenu;
import de.butzlabben.missilewars.player.MWPlayer;

/**
 * Replaces the map vote menu of a player. Opening the menu (via /mw mapmenu or
 * the item of the game join menu) opens the vote GUI of the addon.
 */
public class AddonVoteMenu extends MapVoteMenu {

    private final MapVotingAddon addon;
    private final MWPlayer mwPlayer;

    public AddonVoteMenu(MapVotingAddon addon, MWPlayer mwPlayer) {
        super(mwPlayer);
        this.addon = addon;
        this.mwPlayer = mwPlayer;
    }

    @Override
    public void openMenu() {
        addon.getGuiManager().open(mwPlayer, 0);
    }
}
