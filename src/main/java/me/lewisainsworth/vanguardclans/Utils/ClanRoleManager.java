package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.VanguardClan;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ClanRoleManager {

    public static final String ROLE_LEADER = "leader";
    public static final String ROLE_CO_LEADER = "co-leader";
    public static final String ROLE_MEMBER = "member";

    private final VanguardClan plugin;

    public ClanRoleManager(VanguardClan plugin) {
        this.plugin = plugin;
    }

    public boolean isLeader(Player player, String clanName) {
        if (player == null || clanName == null || clanName.isEmpty()) {
            return false;
        }
        String leader = plugin.getStorageProvider().getClanLeader(clanName);
        return leader != null && leader.equalsIgnoreCase(player.getName());
    }

    public boolean isCoLeader(Player player, String clanName) {
        if (player == null || clanName == null || clanName.isEmpty()) {
            return false;
        }
        String role = plugin.getStorageProvider().getPlayerRole(clanName, player.getName());
        return ROLE_CO_LEADER.equalsIgnoreCase(normalizeRole(role));
    }

    public String getPlayerRole(String clanName, String playerName) {
        if (clanName == null || clanName.isEmpty() || playerName == null || playerName.isEmpty()) {
            return ROLE_MEMBER;
        }
        String leader = plugin.getStorageProvider().getClanLeader(clanName);
        if (leader != null && leader.equalsIgnoreCase(playerName)) {
            return ROLE_LEADER;
        }
        String role = plugin.getStorageProvider().getPlayerRole(clanName, playerName);
        return normalizeRole(role);
    }

    public boolean hasPermission(Player player, String clanName, ClanPermission permission) {
        if (player == null || clanName == null || clanName.isEmpty() || permission == null) {
            return false;
        }
        if (isLeader(player, clanName) || isCoLeader(player, clanName)) {
            return true;
        }

        String role = getPlayerRole(clanName, player.getName());
        if (ROLE_MEMBER.equalsIgnoreCase(role)) {
            return false;
        }

        Map<String, Set<ClanPermission>> roles = getClanRolePermissions(clanName);
        Set<ClanPermission> perms = roles.get(role.toLowerCase(Locale.ROOT));
        return perms != null && perms.contains(permission);
    }

    public Set<String> getAssignableRoles(String clanName) {
        Map<String, Set<ClanPermission>> roles = getClanRolePermissions(clanName);
        Set<String> result = new HashSet<>();
        result.add(ROLE_MEMBER);
        result.add(ROLE_CO_LEADER);
        result.addAll(roles.keySet());
        return result;
    }

    public Map<String, Set<ClanPermission>> getClanRolePermissions(String clanName) {
        ensureDefaultRoles(clanName);
        Map<String, Set<String>> raw = plugin.getStorageProvider().getClanRoles(clanName);
        Map<String, Set<ClanPermission>> result = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : raw.entrySet()) {
            Set<ClanPermission> permissions = EnumSet.noneOf(ClanPermission.class);
            for (String value : entry.getValue()) {
                ClanPermission.fromKey(value).ifPresent(permissions::add);
            }
            result.put(entry.getKey().toLowerCase(Locale.ROOT), permissions);
        }
        return result;
    }

    public void createRole(String clanName, String role) {
        String normalized = normalizeRole(role);
        if (isReservedRole(normalized)) {
            return;
        }
        plugin.getStorageProvider().setClanRole(clanName, normalized, java.util.Collections.emptySet());
    }

    public void deleteRole(String clanName, String role) {
        String normalized = normalizeRole(role);
        if (isReservedRole(normalized)) {
            return;
        }
        plugin.getStorageProvider().deleteClanRole(clanName, normalized);
    }

    public void setRolePermissions(String clanName, String role, Set<ClanPermission> permissions) {
        String normalized = normalizeRole(role);
        if (isReservedRole(normalized)) {
            return;
        }
        Set<String> raw = new HashSet<>();
        for (ClanPermission permission : permissions) {
            raw.add(permission.getKey());
        }
        plugin.getStorageProvider().setClanRole(clanName, normalized, raw);
    }

    public void setPlayerRole(String clanName, String playerName, String role) {
        String normalized = normalizeRole(role);
        if (ROLE_LEADER.equalsIgnoreCase(normalized)) {
            return;
        }
        plugin.getStorageProvider().setPlayerRole(clanName, playerName, normalized);
    }

    private void ensureDefaultRoles(String clanName) {
        Map<String, Set<String>> roles = plugin.getStorageProvider().getClanRoles(clanName);
        if (!roles.containsKey(ROLE_MEMBER)) {
            plugin.getStorageProvider().setClanRole(clanName, ROLE_MEMBER, java.util.Collections.emptySet());
        }
    }

    private boolean isReservedRole(String role) {
        return ROLE_LEADER.equalsIgnoreCase(role) || ROLE_CO_LEADER.equalsIgnoreCase(role);
    }

    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return ROLE_MEMBER;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT)
            .replace("_", "-");
        if (normalized.equals("coleader") || normalized.equals("co-leader") || normalized.equals("colider") || normalized.equals("co-lider")) {
            return ROLE_CO_LEADER;
        }
        return normalized;
    }
}
