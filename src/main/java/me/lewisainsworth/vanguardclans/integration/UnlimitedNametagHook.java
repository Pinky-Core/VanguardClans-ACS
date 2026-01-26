package me.lewisainsworth.vanguardclans.integration;

import me.lewisainsworth.vanguardclans.VanguardClan;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class UnlimitedNametagHook {

    private final VanguardClan plugin;
    private int taskId = -1;
    private boolean reflectionReady = false;
    private Class<?> untApiClass;
    private Method getInstanceMethod;
    private Method getPacketDisplayTextMethod;
    private Method optionalIsPresentMethod;
    private Method optionalGetMethod;
    private Method hideFromPlayerMethod;
    private Method showToPlayerMethod;
    private boolean reflectionFailed = false;

    public UnlimitedNametagHook(VanguardClan plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!isEnabled()) {
            stop();
            return;
        }
        if (!ensureReflection()) {
            stop();
            return;
        }
        stop();
        long intervalTicks = Math.max(20L, plugin.getFH().getConfig().getLong("nametag-privacy.refresh-interval-ticks", 100));
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, intervalTicks).getTaskId();
        plugin.getLogger().info("UnlimitedNameTags privacy hook activated (provider=unt).");
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void refreshAll() {
        if (!isEnabled()) {
            stop();
            return;
        }

        Map<String, String> clanSnapshot = snapshotClans();
        String bypassPermission = plugin.getFH().getConfig().getString("nametag-privacy.bypass-permission", "");
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();

        for (Player viewer : players) {
            boolean viewerBypass = hasBypass(viewer, bypassPermission);
            String viewerClan = clanSnapshot.get(viewer.getName());
            for (Player target : players) {
                if (viewer.equals(target)) {
                    showToPlayer(target, viewer);
                    continue;
                }
                Relation relation = relationOf(viewer, viewerClan, target, clanSnapshot);
                applyMode(viewer, target, relation, viewerBypass);
            }
        }
    }

    private boolean ensureReflection() {
        if (reflectionReady) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            untApiClass = Class.forName("org.alexdev.unlimitednametags.api.UNTAPI");
            getInstanceMethod = untApiClass.getMethod("getInstance");
            getPacketDisplayTextMethod = untApiClass.getMethod("getPacketDisplayText", Player.class);
            Class<?> optionalClass = Class.forName("java.util.Optional");
            optionalIsPresentMethod = optionalClass.getMethod("isPresent");
            optionalGetMethod = optionalClass.getMethod("get");
            Class<?> packetClass = Class.forName("org.alexdev.unlimitednametags.packet.PacketNameTag");
            hideFromPlayerMethod = packetClass.getMethod("hideFromPlayer", Player.class);
            showToPlayerMethod = packetClass.getMethod("showToPlayer", Player.class);
            reflectionReady = true;
            return true;
        } catch (Exception e) {
            if (!reflectionFailed) {
                reflectionFailed = true;
                plugin.getLogger().warning("UnlimitedNameTags API unavailable; skipping provider.");
            }
            return false;
        }
    }

    private void applyMode(Player viewer, Player target, Relation relation, boolean viewerBypass) {
        Object packet = getPacket(target);
        if (packet == null) {
            return;
        }

        if (viewerBypass) {
            showPacketToViewer(packet, viewer);
            return;
        }

        String legacyMode = plugin.getFH().getConfig().getString("nametag-privacy.mode", "hide").toLowerCase(Locale.ROOT);
        switch (legacyMode) {
            case "reset" -> showPacketToViewer(packet, viewer);
            case "obfuscate", "hide" -> {
                if (relation == Relation.ENEMY) {
                    hidePacketFromViewer(packet, viewer);
                } else {
                    showPacketToViewer(packet, viewer);
                }
            }
            default -> showPacketToViewer(packet, viewer);
        }
    }

    private Object getPacket(Player target) {
        try {
            Object api = getInstanceMethod.invoke(null);
            if (api == null) {
                return null;
            }
            Object optional = getPacketDisplayTextMethod.invoke(api, target);
            if (optional == null || !(Boolean) optionalIsPresentMethod.invoke(optional)) {
                return null;
            }
            return optionalGetMethod.invoke(optional);
        } catch (Exception e) {
            return null;
        }
    }

    private void hidePacketFromViewer(Object packet, Player viewer) {
        try {
            hideFromPlayerMethod.invoke(packet, viewer);
        } catch (Exception ignored) {
        }
    }

    private void showPacketToViewer(Object packet, Player viewer) {
        try {
            showToPlayerMethod.invoke(packet, viewer);
        } catch (Exception ignored) {
        }
    }

    private void showToPlayer(Player target, Player viewer) {
        Object packet = getPacket(target);
        if (packet != null) {
            showPacketToViewer(packet, viewer);
        }
    }

    private Map<String, String> snapshotClans() {
        Map<String, String> map = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            map.put(player.getName(), plugin.getPlayerClan(player.getName()));
        }
        return map;
    }

    private Relation relationOf(Player viewer, String viewerClan, Player target, Map<String, String> clanSnapshot) {
        if (viewer.equals(target)) {
            return Relation.SAME;
        }
        String targetClan = clanSnapshot.get(target.getName());
        if (viewerClan != null && viewerClan.equalsIgnoreCase(targetClan)) {
            return Relation.SAME;
        }
        if (targetClan == null || targetClan.trim().isEmpty()) {
            return Relation.NO_CLAN;
        }
        return Relation.ENEMY;
    }

    private boolean hasBypass(Player player, String permission) {
        return player != null && permission != null && !permission.trim().isEmpty() && player.hasPermission(permission.trim());
    }

    private boolean isEnabled() {
        FileConfiguration config = plugin.getFH().getConfig();
        String provider = config.getString("nametag-privacy.provider", "internal");
        boolean enabled = config.getBoolean("nametag-privacy.enabled", false);
        return enabled && "unt".equalsIgnoreCase(provider) && Bukkit.getPluginManager().isPluginEnabled("UnlimitedNameTags");
    }

    private enum Relation {
        SAME,
        ENEMY,
        NO_CLAN
    }
}
