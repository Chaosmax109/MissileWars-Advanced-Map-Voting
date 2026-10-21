package de.mwaddon.mapvoting;

import de.butzlabben.missilewars.configuration.Config;
import de.butzlabben.missilewars.configuration.arena.ArenaConfig;
import de.butzlabben.missilewars.game.Game;
import de.butzlabben.missilewars.game.MapVoting;
import de.butzlabben.missilewars.menus.MenuItem;
import de.butzlabben.missilewars.menus.inventory.MapVoteMenu;
import de.butzlabben.missilewars.player.MWPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The map vote GUI of the addon.
 *
 * Layout (6 rows): rows 1-5 = maps (45 per page), last row = navigation.
 * Left-click on a map = add vote, right-click = remove vote.
 */
public class VoteGuiManager implements Listener {

    private static final int SIZE = 54;
    private static final int MAPS_PER_PAGE = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_CLEAR = 51;
    private static final int SLOT_NEXT = 53;
    private static final int BAR_LENGTH = 10;

    private final MapVotingAddon addon;
    /** player UUID -> open GUI */
    private final Map<UUID, Holder> openGuis = new HashMap<>();

    public VoteGuiManager(MapVotingAddon addon) {
        this.addon = addon;
    }

    /** Inventory holder to identify the GUI. */
    public static class Holder implements InventoryHolder {
        private final MWPlayer mwPlayer;
        private final int page;
        private final Map<Integer, ArenaConfig> slots = new HashMap<>();
        private Inventory inventory;

        Holder(MWPlayer mwPlayer, int page) {
            this.mwPlayer = mwPlayer;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(MWPlayer mwPlayer, int page) {
        Player player = mwPlayer.getPlayer();
        Holder holder = new Holder(mwPlayer, page);
        // Same title as the original menu, so the lobby listener of MissileWars allows the inventory.
        holder.inventory = Bukkit.createInventory(holder, SIZE, MapVoteMenu.getTitle());
        fill(holder);
        player.openInventory(holder.inventory);
        openGuis.put(player.getUniqueId(), holder);
    }

    private int getMaxPage(Game game) {
        int arenas = game.getGameConfig().getArenas().size();
        return Math.max(0, (arenas - 1) / MAPS_PER_PAGE);
    }

    private void fill(Holder holder) {
        Inventory inv = holder.inventory;
        inv.clear();
        holder.slots.clear();

        MWPlayer mwPlayer = holder.mwPlayer;
        Game game = mwPlayer.getGame();
        MapVoting voting = game.getMapVoting();
        AddonMapVoting addonVoting = voting instanceof AddonMapVoting ? (AddonMapVoting) voting : null;
        Messages msg = addon.getMessages();
        Player player = mwPlayer.getPlayer();

        int maxVotes = AddonMapVoting.getMaxVotes(player);
        int usedVotes = addonVoting == null ? 0 : addonVoting.getUsedVotes(player.getUniqueId());

        List<ArenaConfig> arenas = game.getGameConfig().getArenas();
        int start = holder.page * MAPS_PER_PAGE;
        for (int i = 0; i < MAPS_PER_PAGE && start + i < arenas.size(); i++) {
            ArenaConfig arena = arenas.get(start + i);
            int ownVotes = addonVoting == null ? 0 : addonVoting.getVotesOfPlayer(arena, mwPlayer);
            int totalVotes = addonVoting == null ? 0 : addonVoting.getVotesOf(arena);
            double percent = voting.getPercentOf(arena);

            Material material = arena.getDisplayMaterial() == null ? null : Material.matchMaterial(arena.getDisplayMaterial());
            if (material == null) material = Material.PAPER;
            ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, ownVotes)));
            MenuItem.hideMetaValues(item);
            MenuItem.setDisplayName(item, Config.MapVoteMenuItems.MAP_ITEM.getMessage()
                    .replace("{arena-name}", arena.getDisplayName()));
            if (ownVotes > 0) MenuItem.setEnchantment(item);

            List<String> lore = new ArrayList<>();
            lore.add(msg.raw("menu-lore-percent")
                    .replace("%bar%", bar(percent))
                    .replace("%percent%", Long.toString(Math.round(percent))));
            lore.add(msg.raw("menu-lore-total").replace("%votes%", Integer.toString(totalVotes)));
            lore.add(msg.raw("menu-lore-own").replace("%votes%", Integer.toString(ownVotes)));
            if (maxVotes > 1) {
                lore.add(msg.raw("menu-lore-left")
                        .replace("%left%", Integer.toString(Math.max(0, maxVotes - usedVotes)))
                        .replace("%max%", Integer.toString(maxVotes)));
            }
            lore.add(" ");
            lore.add(msg.raw("menu-lore-hint-add"));
            if (maxVotes > 1 && AddonMapVoting.canStackVotes(player)) lore.add(msg.raw("menu-lore-hint-add-all"));
            lore.add(msg.raw("menu-lore-hint-remove"));
            lore.add(msg.raw("menu-lore-hint-clear"));
            setLore(item, lore);

            inv.setItem(i, item);
            holder.slots.put(i, arena);
        }

        // Navigation
        int maxPage = getMaxPage(game);
        ItemStack back = new ItemStack(holder.page > 0 ? Material.ARROW : Material.GRAY_DYE);
        MenuItem.setDisplayName(back, holder.page > 0
                ? Config.MapVoteMenuItems.BACKWARDS_ITEM_ACTIVE.getMessage()
                : Config.MapVoteMenuItems.BACKWARDS_ITEM_INACTIVE.getMessage());
        inv.setItem(SLOT_BACK, back);

        ItemStack next = new ItemStack(holder.page < maxPage ? Material.ARROW : Material.GRAY_DYE);
        MenuItem.setDisplayName(next, holder.page < maxPage
                ? Config.MapVoteMenuItems.FORWARDS_ITEM_ACTIVE.getMessage()
                : Config.MapVoteMenuItems.FORWARDS_ITEM_INACTIVE.getMessage());
        inv.setItem(SLOT_NEXT, next);

        ItemStack info = new ItemStack(Material.BOOK);
        MenuItem.hideMetaValues(info);
        MenuItem.setDisplayName(info, msg.raw("menu-info-name"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(msg.raw("menu-info-lore")
                .replace("%used%", Integer.toString(usedVotes))
                .replace("%max%", Integer.toString(maxVotes)));
        setLore(info, infoLore);
        inv.setItem(SLOT_INFO, info);

        // Remove all own votes
        ItemStack clear = new ItemStack(Material.BARRIER);
        MenuItem.hideMetaValues(clear);
        MenuItem.setDisplayName(clear, msg.raw("menu-clear-name"));
        List<String> clearLore = new ArrayList<>();
        clearLore.add(msg.raw("menu-clear-lore")
                .replace("%used%", Integer.toString(usedVotes))
                .replace("%max%", Integer.toString(maxVotes)));
        setLore(clear, clearLore);
        inv.setItem(SLOT_CLEAR, clear);
    }

    private static void setLore(ItemStack item, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static String bar(double percent) {
        int filled = (int) Math.round(percent / 100.0 * BAR_LENGTH);
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < BAR_LENGTH; i++) {
            if (i == filled) sb.append("§7");
            sb.append('■');
        }
        return sb.toString();
    }

    /** Updates the open GUIs of all players of the game. */
    public void refresh(Game game) {
        for (Holder holder : new ArrayList<>(openGuis.values())) {
            if (holder.mwPlayer.getGame() == game) fill(holder);
        }
    }

    public void closeAll(Game game) {
        for (Holder holder : new ArrayList<>(openGuis.values())) {
            if (holder.mwPlayer.getGame() == game) holder.mwPlayer.getPlayer().closeInventory();
        }
    }

    public void closeAll() {
        for (Holder holder : new ArrayList<>(openGuis.values())) {
            holder.mwPlayer.getPlayer().closeInventory();
        }
        openGuis.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof Holder)) return;
        event.setCancelled(true);

        Holder holder = (Holder) top.getHolder();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        MWPlayer mwPlayer = holder.mwPlayer;
        Player player = mwPlayer.getPlayer();
        Game game = mwPlayer.getGame();

        if (slot == SLOT_BACK) {
            if (holder.page > 0) open(mwPlayer, holder.page - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            if (holder.page < getMaxPage(game)) open(mwPlayer, holder.page + 1);
            return;
        }

        if (slot == SLOT_CLEAR) {
            if (!player.hasPermission("mw.vote")) return;
            MapVoting voting = game.getMapVoting();
            if (voting instanceof AddonMapVoting) ((AddonMapVoting) voting).removeAllVotes(player);
            return;
        }

        ArenaConfig arena = holder.slots.get(slot);
        if (arena == null) return;
        if (!player.hasPermission("mw.vote")) return;

        MapVoting voting = game.getMapVoting();
        if (event.isRightClick() && event.isShiftClick()) {
            if (voting instanceof AddonMapVoting) ((AddonMapVoting) voting).removeAllVotes(player);
        } else if (event.isRightClick()) {
            if (voting instanceof AddonMapVoting) ((AddonMapVoting) voting).removeVote(player, arena.getName());
        } else if (event.isShiftClick()) {
            if (voting instanceof AddonMapVoting) ((AddonMapVoting) voting).addAllVotes(player, arena.getName());
        } else {
            voting.addVote(player, arena.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        UUID uuid = event.getPlayer().getUniqueId();
        Holder holder = openGuis.get(uuid);
        if (holder != null && holder.inventory == event.getInventory()) openGuis.remove(uuid);
    }
}
