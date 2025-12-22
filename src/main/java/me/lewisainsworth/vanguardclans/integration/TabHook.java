package me.lewisainsworth.vanguardclans.integration;

import me.lewisainsworth.vanguardclans.VanguardClan;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Per-viewer nametag control using TAB API.
 * - hide: oculta el tag del enemigo
 * - reset: muestra limpio
 * - obfuscate: aplica prefijo/sufijo (&k ... &r) sobre todo el tag del enemigo
 */
public class TabHook {

    private final VanguardClan plugin;
    private int taskId = -1;

    public TabHook(VanguardClan plugin) {
        this.plugin = plugin;
    }

    public void start() {
        TabAPI api = TabAPI.getInstance();
        if (api == null) {
            plugin.getLogger().warning("TAB API no disponible; no se aplica privacidad via TAB.");
            return;
        }
        stop();
        long period = Math.max(20L, plugin.getConfig().getLong("nametag-privacy.refresh-interval-ticks", 100));
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, period).getTaskId();
        plugin.getLogger().info("Privacidad de nametag via TAB activada (se refresca cada " + period + " ticks).");
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        TabAPI api = TabAPI.getInstance();
        if (api != null) {
            NameTagManager nm = api.getNameTagManager();
            if (nm != null) {
                for (TabPlayer viewer : api.getOnlinePlayers()) {
                    for (TabPlayer target : api.getOnlinePlayers()) {
                        nm.showNameTag(viewer, target);
                    }
                }
            }
        }
    }

    private void refreshAll() {
        FileConfiguration config = plugin.getFH().getConfig();
        if (!isEnabledForTab(config)) {
            return;
        }

        TabAPI api = TabAPI.getInstance();
        if (api == null) return;
        NameTagManager nm = api.getNameTagManager();
        if (nm == null) return;

        Map<String, String> clans = snapshotClans(api);
        String bypass = config.getString("nametag-privacy.bypass-permission", "");
        String legacyMode = config.getString("nametag-privacy.mode", "hide").toLowerCase(Locale.ROOT);
        // TAB API no permite prefijo/sufijo per-viewer; obfuscate se degrada a hide

        for (TabPlayer viewer : api.getOnlinePlayers()) {
            Player viewerBukkit = (Player) viewer.getPlayer();
            boolean viewerBypass = hasBypass(viewerBukkit, bypass);
            for (TabPlayer target : api.getOnlinePlayers()) {
                if (viewer.equals(target)) {
                    clearCustom(nm, target);
                    continue;
                }
                Relation relation = relationOf(viewer, target, clans);
                applyMode(nm, viewer, target, viewerBypass, relation, legacyMode);
            }
        }
    }

    private void applyMode(NameTagManager nm, TabPlayer viewer, TabPlayer target, boolean viewerBypass, Relation relation,
                           String legacyMode) {
        // Reset custom (TAB API es global per jugador, no per viewer)
        clearCustom(nm, target);

        if (viewerBypass) {
            nm.showNameTag(viewer, target);
            return;
        }

        switch (legacyMode) {
            case "hide" -> {
                if (relation == Relation.ENEMY) {
                    nm.hideNameTag(target, viewer); // ocultar target para el viewer
                } else {
                    nm.showNameTag(target, viewer);
                }
            }
            case "reset" -> nm.showNameTag(target, viewer);
            case "obfuscate" -> {
                if (relation == Relation.ENEMY) {
                    nm.hideNameTag(target, viewer); // degradado a hide para enemigos
                } else {
                    nm.showNameTag(target, viewer);
                }
            }
            default -> nm.showNameTag(target, viewer);
        }
    }

    private void clearCustom(NameTagManager nm, TabPlayer target) {
        nm.setPrefix(target, null);
        nm.setSuffix(target, null);
        nm.showNameTag(target);
    }

    private boolean isEnabledForTab(FileConfiguration config) {
        boolean enabled = config.getBoolean("nametag-privacy.enabled", false);
        String provider = config.getString("nametag-privacy.provider", "internal");
        return enabled && "tab".equalsIgnoreCase(provider);
    }

    private boolean hasBypass(Player viewer, String bypass) {
        return viewer != null && bypass != null && !bypass.trim().isEmpty() && viewer.hasPermission(bypass.trim());
    }

    private Map<String, String> snapshotClans(TabAPI api) {
        Map<String, String> map = new HashMap<>();
        for (TabPlayer p : api.getOnlinePlayers()) {
            Player bukkit = (Player) p.getPlayer();
            map.put(p.getName(), plugin.getPlayerClan(bukkit.getName()));
        }
        return map;
    }

    private Relation relationOf(TabPlayer viewer, TabPlayer target, Map<String, String> clans) {
        if (viewer.equals(target)) return Relation.SAME;
        String viewerClan = clans.get(viewer.getName());
        String targetClan = clans.get(target.getName());

        if (!isEmpty(viewerClan) && viewerClan.equalsIgnoreCase(targetClan)) {
            return Relation.SAME;
        }
        if (isEmpty(targetClan)) {
            return Relation.NO_CLAN;
        }
        return Relation.ENEMY;
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private enum Relation { SAME, ENEMY, NO_CLAN }

    private String color(String in) {
        if (in == null || in.isEmpty()) return "";
        return ChatColor.translateAlternateColorCodes('&', in);
    }
}
