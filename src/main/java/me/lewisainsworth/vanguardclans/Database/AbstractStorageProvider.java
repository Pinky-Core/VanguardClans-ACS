package me.lewisainsworth.vanguardclans.Database;

import me.lewisainsworth.vanguardclans.VanguardClan;
import me.lewisainsworth.vanguardclans.Utils.LangManager;
import me.lewisainsworth.vanguardclans.Utils.MSG;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractStorageProvider implements StorageProvider {
    
    protected final VanguardClan plugin;
    protected final FileConfiguration config;
    protected final LangManager langManager;
    
    // Cache for performance
    protected final Map<String, String> playerClanCache = new ConcurrentHashMap<>();
    protected final Set<String> clanNamesCache = ConcurrentHashMap.newKeySet();
    protected final Map<String, String> clanColoredNameCache = new ConcurrentHashMap<>();
    protected long lastCacheUpdate = 0;
    
    public AbstractStorageProvider(FileConfiguration config) {
        this.config = config;
        this.plugin = VanguardClan.getInstance();
        this.langManager = plugin.getLangManager();
    }
    
    @Override
    public String getPlayerClan(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return null;
        }
        
        ensureCacheFresh();
        return playerClanCache.get(playerName.toLowerCase());
    }
    
    @Override
    public List<String> getClanMembers(String clanName) {
        List<String> members = new ArrayList<>();
        try {
            // This will be implemented by each specific provider
            members = getClanMembersImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting clan members for " + clanName + ": " + e.getMessage());
        }
        return members;
    }
    
    @Override
    public Set<String> getAllClans() {
        ensureCacheFresh();
        return new HashSet<>(clanNamesCache);
    }
    
    @Override
    public boolean clanExists(String clanName) {
        if (clanName == null || clanName.trim().isEmpty()) {
            return false;
        }
        ensureCacheFresh();
        for (String cached : clanNamesCache) {
            if (cached.equalsIgnoreCase(clanName)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean isPlayerInClan(String playerName, String clanName) {
        if (playerName == null || clanName == null) {
            return false;
        }
        String playerClan = getPlayerClan(playerName);
        return playerClan != null && playerClan.equalsIgnoreCase(clanName);
    }
    
    @Override
    public boolean isClanBanned(String clanName) {
        if (clanName == null || clanName.trim().isEmpty()) {
            return false;
        }
        try {
            return isClanBannedImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking if clan is banned: " + clanName + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public List<String> getPlayerInvites(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return getPlayerInvitesImpl(playerName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting player invites for: " + playerName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public int getClanMemberCount(String clanName) {
        if (clanName == null || clanName.trim().isEmpty()) {
            return 0;
        }
        try {
            return getClanMemberCountImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting clan member count for: " + clanName + ": " + e.getMessage());
            return 0;
        }
    }
    
    @Override
    public boolean isFriendlyFireAlliesEnabled(String clanName) {
        if (clanName == null || clanName.trim().isEmpty()) {
            return false;
        }
        try {
            return isFriendlyFireAlliesEnabledImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking friendly fire allies for: " + clanName + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean areClansAllied(String clan1, String clan2) {
        if (clan1 == null || clan2 == null || clan1.trim().isEmpty() || clan2.trim().isEmpty()) {
            return false;
        }
        if (clan1.equalsIgnoreCase(clan2)) {
            return true;
        }
        try {
            return areClansAlliedImpl(clan1, clan2);
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking alliance between: " + clan1 + " and " + clan2 + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void incrementPlayerKills(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return;
        }
        try {
            incrementPlayerKillsImpl(playerName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error incrementing kills for: " + playerName + ": " + e.getMessage());
        }
    }
    
    @Override
    public void incrementPlayerDeaths(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return;
        }
        try {
            incrementPlayerDeathsImpl(playerName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error incrementing deaths for: " + playerName + ": " + e.getMessage());
        }
    }
    
    @Override
    public Location getClanHome(String clanName) {
        try {
            return getClanHomeImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting clan home for " + clanName + ": " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public void setClanHome(String clanName, Location location) {
        try {
            setClanHomeImpl(clanName, location);
        } catch (Exception e) {
            plugin.getLogger().severe("Error setting clan home for " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public List<String> getClanAlliances(String clanName) {
        try {
            return getClanAlliancesImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting clan alliances for " + clanName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public void createAlliance(String clan1, String clan2, boolean friendlyFire) {
        try {
            createAllianceImpl(clan1, clan2, friendlyFire);
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating alliance between " + clan1 + " and " + clan2 + ": " + e.getMessage());
        }
    }
    
    @Override
    public void removeAlliance(String clan1, String clan2) {
        try {
            removeAllianceImpl(clan1, clan2);
        } catch (Exception e) {
            plugin.getLogger().severe("Error removing alliance between " + clan1 + " and " + clan2 + ": " + e.getMessage());
        }
    }
    
    @Override
    public boolean getFriendlyFire(String clanName) {
        try {
            return getFriendlyFireImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting friendly fire setting for " + clanName + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void setFriendlyFire(String clanName, boolean enabled) {
        try {
            setFriendlyFireImpl(clanName, enabled);
        } catch (Exception e) {
            plugin.getLogger().severe("Error setting friendly fire for " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public List<String> getClanInvites(String clanName) {
        try {
            return getClanInvitesImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting clan invites for " + clanName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void cleanupExpiredInvites() {
        try {
            cleanupExpiredInvitesImpl(getInviteCutoff());
        } catch (Exception e) {
            plugin.getLogger().warning("Error cleaning up expired clan invites: " + e.getMessage());
        }
    }

    @Override
    public void addClanInvite(String clanName, String playerName) {
        try {
            addClanInviteImpl(clanName, playerName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error adding clan invite for " + playerName + " to " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public void removeClanInvite(String clanName, String playerName) {
        try {
            removeClanInviteImpl(clanName, playerName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error removing clan invite for " + playerName + " from " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public boolean isPlayerInvitedToClan(String playerName, String clanName) {
        try {
            return isPlayerInvitedToClanImpl(playerName, clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking if " + playerName + " is invited to " + clanName + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public List<String> getPendingAlliances(String clanName) {
        try {
            return getPendingAlliancesImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting pending alliances for " + clanName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public void addPendingAlliance(String clan1, String clan2) {
        try {
            addPendingAllianceImpl(clan1, clan2);
        } catch (Exception e) {
            plugin.getLogger().severe("Error adding pending alliance between " + clan1 + " and " + clan2 + ": " + e.getMessage());
        }
    }
    
    @Override
    public void removePendingAlliance(String clan1, String clan2) {
        try {
            removePendingAllianceImpl(clan1, clan2);
        } catch (Exception e) {
            plugin.getLogger().severe("Error removing pending alliance between " + clan1 + " and " + clan2 + ": " + e.getMessage());
        }
    }
    
    @Override
    public List<Map<String, Object>> getClanReports(String clanName) {
        try {
            return getClanReportsImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting clan reports for " + clanName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public void addClanReport(String clanName, String reporter, String reason, long timestamp) {
        try {
            addClanReportImpl(clanName, reporter, reason, timestamp);
        } catch (Exception e) {
            plugin.getLogger().severe("Error adding clan report for " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public List<Map<String, Object>> getPlayerClanHistory(String playerName) {
        try {
            return getPlayerClanHistoryImpl(playerName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting player clan history for " + playerName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public void addPlayerClanHistory(String playerName, String clanName, String action, long timestamp) {
        try {
            addPlayerClanHistoryImpl(playerName, clanName, action, timestamp);
        } catch (Exception e) {
            plugin.getLogger().severe("Error adding player clan history for " + playerName + ": " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("playerClanCacheSize", playerClanCache.size());
        stats.put("clanNamesCacheSize", clanNamesCache.size());
        stats.put("clanColoredNameCacheSize", clanColoredNameCache.size());
        stats.put("lastCacheUpdate", lastCacheUpdate);
        return stats;
    }
    
    @Override
    public String getCachedPlayerClan(String playerName) {
        return getPlayerClan(playerName);
    }
    
    @Override
    public Set<String> getCachedClanNames() {
        return getAllClans();
    }
    
    @Override
    public String getColoredClanName(String clanName) {
        return getClanColoredName(clanName);
    }
    
    @Override
    public void deleteClanHome(String clanName) {
        setClanHome(clanName, null);
    }
    
    @Override
    public double getKillDeathRatio(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return 0.0;
        }
        int kills = getPlayerKills(playerName);
        int deaths = getPlayerDeaths(playerName);
        if (deaths <= 0) {
            return kills;
        }
        return (double) kills / deaths;
    }

    @Override
    public int getPlayerKills(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return 0;
        }
        try {
            return getPlayerKillsImpl(playerName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting player kills for: " + playerName + ": " + e.getMessage());
            return 0;
        }
    }

    @Override
    public int getPlayerDeaths(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return 0;
        }
        try {
            return getPlayerDeathsImpl(playerName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting player deaths for: " + playerName + ": " + e.getMessage());
            return 0;
        }
    }
    
    @Override
    public void fixClanColorsAsync(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                fixClanColorsImpl(sender);
            } catch (Exception e) {
                plugin.getLogger().severe("Error fixing clan colors: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(MSG.color(langManager.getMessage("msg.admin_fix_error")))
                );
            }
        });
    }
    
    @Override
    public void repairClanLeadership(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repairClanLeadershipImpl(sender);
            } catch (Exception e) {
                plugin.getLogger().severe("Error repairing clan leadership: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(MSG.color(langManager.getMessage("msg.admin_repair_error")))
                );
            }
        });
    }

    @Override
    public boolean isFriendlyFireEnabled(String clanName) {
        try {
            return isFriendlyFireEnabledImpl(clanName);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void setFriendlyFireEnabled(String clanName, boolean enabled) {
        try {
            setFriendlyFireEnabledImpl(clanName, enabled);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateClanName(String oldName, String newName, String newColoredName) {
        try {
            updateClanNameImpl(oldName, newName, newColoredName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setClanColoredName(String clanName, String coloredName) {
        if (clanName == null || clanName.trim().isEmpty()) {
            return;
        }
        try {
            updateClanNameImpl(clanName, clanName, coloredName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getPlayerRole(String clanName, String playerName) {
        if (clanName == null || clanName.trim().isEmpty() || playerName == null || playerName.trim().isEmpty()) {
            return "member";
        }
        try {
            String role = getPlayerRoleImpl(clanName, playerName);
            return role == null || role.trim().isEmpty() ? "member" : role;
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting player role for: " + playerName + ": " + e.getMessage());
            return "member";
        }
    }

    @Override
    public void setPlayerRole(String clanName, String playerName, String role) {
        if (clanName == null || clanName.trim().isEmpty() || playerName == null || playerName.trim().isEmpty()) {
            return;
        }
        try {
            setPlayerRoleImpl(clanName, playerName, role);
        } catch (Exception e) {
            plugin.getLogger().severe("Error setting player role for: " + playerName + ": " + e.getMessage());
        }
    }

    @Override
    public Map<String, Set<String>> getClanRoles(String clanName) {
        if (clanName == null || clanName.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return getClanRolesImpl(clanName);
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting clan roles for: " + clanName + ": " + e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public void setClanRole(String clanName, String role, Set<String> permissions) {
        if (clanName == null || clanName.trim().isEmpty() || role == null || role.trim().isEmpty()) {
            return;
        }
        try {
            setClanRoleImpl(clanName, role, permissions == null ? Collections.emptySet() : permissions);
        } catch (Exception e) {
            plugin.getLogger().severe("Error setting clan role for: " + clanName + ": " + e.getMessage());
        }
    }

    @Override
    public void deleteClanRole(String clanName, String role) {
        if (clanName == null || clanName.trim().isEmpty() || role == null || role.trim().isEmpty()) {
            return;
        }
        try {
            deleteClanRoleImpl(clanName, role);
        } catch (Exception e) {
            plugin.getLogger().severe("Error deleting clan role for: " + clanName + ": " + e.getMessage());
        }
    }
    
    // Abstract methods that must be implemented by each provider
    protected abstract List<String> getClanMembersImpl(String clanName) throws Exception;
    protected abstract boolean isClanBannedImpl(String clanName) throws Exception;
    protected abstract List<String> getPlayerInvitesImpl(String playerName) throws Exception;
    protected abstract int getClanMemberCountImpl(String clanName) throws Exception;
    protected abstract boolean isFriendlyFireAlliesEnabledImpl(String clanName) throws Exception;
    protected abstract boolean areClansAlliedImpl(String clan1, String clan2) throws Exception;
    protected abstract void incrementPlayerKillsImpl(String playerName) throws Exception;
    protected abstract void incrementPlayerDeathsImpl(String playerName) throws Exception;
    protected abstract int getPlayerKillsImpl(String playerName) throws Exception;
    protected abstract int getPlayerDeathsImpl(String playerName) throws Exception;
    protected abstract Location getClanHomeImpl(String clanName) throws Exception;
    protected abstract void setClanHomeImpl(String clanName, Location location) throws Exception;
    protected abstract List<String> getClanAlliancesImpl(String clanName) throws Exception;
    protected abstract void createAllianceImpl(String clan1, String clan2, boolean friendlyFire) throws Exception;
    protected abstract void removeAllianceImpl(String clan1, String clan2) throws Exception;
    protected abstract boolean getFriendlyFireImpl(String clanName) throws Exception;
    protected abstract void setFriendlyFireImpl(String clanName, boolean enabled) throws Exception;
    protected abstract List<String> getClanInvitesImpl(String clanName) throws Exception;
    protected abstract void cleanupExpiredInvitesImpl(long cutoff) throws Exception;
    protected abstract void addClanInviteImpl(String clanName, String playerName) throws Exception;
    protected abstract void removeClanInviteImpl(String clanName, String playerName) throws Exception;
    protected abstract boolean isPlayerInvitedToClanImpl(String playerName, String clanName) throws Exception;
    protected abstract List<String> getPendingAlliancesImpl(String clanName) throws Exception;
    protected abstract void addPendingAllianceImpl(String clan1, String clan2) throws Exception;
    protected abstract void removePendingAllianceImpl(String clan1, String clan2) throws Exception;
    protected abstract List<Map<String, Object>> getClanReportsImpl(String clanName) throws Exception;
    protected abstract void addClanReportImpl(String clanName, String reporter, String reason, long timestamp) throws Exception;
    protected abstract List<Map<String, Object>> getPlayerClanHistoryImpl(String playerName) throws Exception;
    protected abstract void addPlayerClanHistoryImpl(String playerName, String clanName, String action, long timestamp) throws Exception;
    protected abstract void fixClanColorsImpl(CommandSender sender) throws Exception;
    protected abstract void repairClanLeadershipImpl(CommandSender sender) throws Exception;
    protected abstract boolean isFriendlyFireEnabledImpl(String clanName) throws Exception;
    protected abstract void setFriendlyFireEnabledImpl(String clanName, boolean enabled) throws Exception;
    protected abstract void updateClanNameImpl(String oldName, String newName, String newColoredName) throws Exception;
    protected abstract String getPlayerRoleImpl(String clanName, String playerName) throws Exception;
    protected abstract void setPlayerRoleImpl(String clanName, String playerName, String role) throws Exception;
    protected abstract Map<String, Set<String>> getClanRolesImpl(String clanName) throws Exception;
    protected abstract void setClanRoleImpl(String clanName, String role, Set<String> permissions) throws Exception;
    protected abstract void deleteClanRoleImpl(String clanName, String role) throws Exception;
    
    // Helper method to ensure cache is fresh
    protected void ensureCacheFresh() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheUpdate > 300000) { // 5 minutes
            reloadCache();
        }
    }

    protected long getInviteCutoff() {
        return System.currentTimeMillis() - StorageProvider.INVITE_EXPIRATION_MS;
    }
    
    // Helper method to parse location from string
    protected Location parseLocation(String locationString) {
        if (locationString == null || locationString.trim().isEmpty()) {
            return null;
        }
        
        try {
            String[] parts = locationString.split(",");
            if (parts.length >= 6) {
                World world = Bukkit.getWorld(parts[0]);
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                float yaw = Float.parseFloat(parts[4]);
                float pitch = Float.parseFloat(parts[5]);
                return new Location(world, x, y, z, yaw, pitch);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error parsing location: " + locationString);
        }
        return null;
    }
    
    // Helper method to serialize location to string
    protected String serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        
        return String.format("%s,%.2f,%.2f,%.2f,%.2f,%.2f",
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }
} 
