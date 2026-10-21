package de.mwaddon.mapvoting;

import de.butzlabben.missilewars.configuration.PluginMessages;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Messages of the addon. The texts are read from the "messages" section of
 * the addon config.yml. The prefix of MissileWars is used.
 */
public class Messages {

    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("vote-success", "&7Du hast einen Vote an %map% &7vergeben. Deine Votes: &e%used%&7/&e%max%");
        DEFAULTS.put("vote-success-all", "&7Du hast &e%amount% &7Votes an %map% &7vergeben. Deine Votes: &e%used%&7/&e%max%");
        DEFAULTS.put("vote-removed", "&7Du hast einen Vote von %map% &7entfernt. Deine Votes: &e%used%&7/&e%max%");
        DEFAULTS.put("vote-removed-all", "&7Du hast alle deine Votes entfernt.");
        DEFAULTS.put("vote-not-voted", "&cDu hast nicht für %map% &cgevotet.");
        DEFAULTS.put("vote-no-votes-given", "&cDu hast noch nicht gevotet.");
        DEFAULTS.put("vote-no-votes-left", "&cDu hast keine Votes mehr übrig (&e%used%&c/&e%max%&c). Entferne einen Vote mit &e/mw unvote <map> &coder per Rechtsklick im Menü.");
        DEFAULTS.put("vote-no-stack", "&cDu hast bereits für %map% &cgevotet. Du darfst nur einen Vote pro Map vergeben.");
        DEFAULTS.put("menu-lore-percent", "&7Ergebnis: %bar% &e%percent%%");
        DEFAULTS.put("menu-lore-total", "&7Votes gesamt: &e%votes%");
        DEFAULTS.put("menu-lore-own", "&7Deine Votes: &e%votes%");
        DEFAULTS.put("menu-lore-left", "&7Übrige Votes: &e%left%&7/&e%max%");
        DEFAULTS.put("menu-lore-hint-add", "&8» Linksklick: Vote geben");
        DEFAULTS.put("menu-lore-hint-add-all", "&8» Shift+Linksklick: alle übrigen Votes geben");
        DEFAULTS.put("menu-lore-hint-remove", "&8» Rechtsklick: Vote entfernen");
        DEFAULTS.put("menu-lore-hint-clear", "&8» Shift+Rechtsklick: alle Votes entfernen");
        DEFAULTS.put("menu-info-name", "&eDeine Votes");
        DEFAULTS.put("menu-info-lore", "&7Vergeben: &e%used%&7/&e%max%");
        DEFAULTS.put("menu-clear-name", "&cAlle Votes entfernen");
        DEFAULTS.put("menu-clear-lore", "&7Entfernt alle deine Votes (&e%used%&7/&e%max%&7).");
        DEFAULTS.put("forcemap-success", "&7Die Map %map% &7wurde für das Spiel %game% &7festgelegt.");
        DEFAULTS.put("forcemap-broadcast", "&7Die Map %map% &7wurde von einem Teammitglied festgelegt.");
        DEFAULTS.put("forcemap-already-selected", "&cDie Map %map% &cist bereits ausgewählt.");
        DEFAULTS.put("forcemap-not-in-lobby", "&cDie Map kann nur geändert werden, solange das Spiel in der Lobby ist.");
        DEFAULTS.put("forcemap-failed", "&cDie Map konnte nicht geändert werden. Details stehen in der Konsole.");
        DEFAULTS.put("forcemap-usage", "&cBenutzung: /mw forcemap <map> [spiel]");
        DEFAULTS.put("reload", "&7Das MapVoting-Addon wurde neu geladen.");
    }

    private final Map<String, String> messages = new HashMap<>();

    public Messages(MapVotingAddon addon) {
        FileConfiguration cfg = addon.getConfig();
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            messages.put(entry.getKey(), cfg.getString("messages." + entry.getKey(), entry.getValue()));
        }
    }

    /** Message without prefix, with color codes. */
    public String raw(String key) {
        String msg = messages.get(key);
        if (msg == null) msg = DEFAULTS.getOrDefault(key, key);
        return PluginMessages.getConvertedMsg(msg);
    }

    /** Message with the MissileWars prefix. */
    public String get(String key) {
        return PluginMessages.getPrefix() + raw(key);
    }
}
