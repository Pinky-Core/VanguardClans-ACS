package me.lewisainsworth.vanguardclans.Database;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface StorageProvider {
    
    /**
     * Initialize the storage provider
     */
    void initialize() throws Exception;
    
    /**
     * Close the storage provider
     */
    void close();
    
    /**
     * Setup database tables
     */
    void setupTables() throws Exception;
    
    /**
     * Get database connection (for SQL-based storage providers)
     */
    java.sql.Connection getConnection() throws java.sql.SQLException;
    
    /**
     * Get a player's clan
     */
    String getPlayerClan(String playerName);
    
    /**
     * Get clan leader
     */
    String getClanLeader(String clanName);
    
    /**
     * Get clan founder
     */
    String getClanFounder(String clanName);
    
    /**
     * Get clan members
     */
    List<String> getClanMembers(String clanName);
    
    /**
     * Get clan money
     */
    double getClanMoney(String clanName);
    
    /**
     * Set clan money
     */
    void setClanMoney(String clanName, double amount);
    
    /**
     * Get clan points (used for slot upgrades)
     */
    int getClanPoints(String clanName);

    /**
     * Set clan points
     */
    void setClanPoints(String clanName, int points);

    /**
     * Add/subtract clan points
     */
    void addClanPoints(String clanName, int delta);

    /**
     * Get how many slot upgrades a clan has purchased
     */
    int getClanSlotUpgrades(String clanName);

    /**
     * Set how many slot upgrades a clan has purchased
     */
    void setClanSlotUpgrades(String clanName, int upgrades);
    
    /**
     * Get clan privacy setting
     */
    String getClanPrivacy(String clanName);
    
    /**
     * Set clan privacy setting
     */
    void setClanPrivacy(String clanName, String privacy);
    
    /**
     * Get clan colored name
     */
    String getClanColoredName(String clanName);
    
    /**
     * Get colored clan name (alias for getClanColoredName)
     */
    String getColoredClanName(String clanName);
    
    /**
     * Create a new clan
     */
    void createClan(String clanName, String coloredName, String founder, String leader, double money, String privacy);
    
    /**
     * Delete a clan
     */
    void deleteClan(String clanName);
    
    /**
     * Add player to clan
     */
    void addPlayerToClan(String playerName, String clanName);
    
    /**
     * Remove player from clan
     */
    void removePlayerFromClan(String playerName, String clanName);
    
    /**
     * Update clan leader
     */
    void updateClanLeader(String clanName, String newLeader);
    
    /**
     * Update clan name
     */
    void updateClanName(String oldName, String newName, String newColoredName);
    
    /**
     * Get all clans
     */
    Set<String> getAllClans();
    
    /**
     * Check if clan exists
     */
    boolean clanExists(String clanName);
    
    /**
     * Check if player is in clan
     */
    boolean isPlayerInClan(String playerName, String clanName);
    
    /**
     * Check if clan is banned
     */
    boolean isClanBanned(String clanName);
    
    /**
     * Get clan home location
     */
    Location getClanHome(String clanName);
    
    /**
     * Set clan home location
     */
    void setClanHome(String clanName, Location location);
    
    /**
     * Delete clan home location
     */
    void deleteClanHome(String clanName);
    
    /**
     * Get clan alliances
     */
    List<String> getClanAlliances(String clanName);
    
    /**
     * Create alliance
     */
    void createAlliance(String clan1, String clan2, boolean friendlyFire);
    
    /**
     * Remove alliance
     */
    void removeAlliance(String clan1, String clan2);
    
    /**
     * Get friendly fire setting
     */
    boolean getFriendlyFire(String clanName);
    
    /**
     * Set friendly fire setting
     */
    void setFriendlyFire(String clanName, boolean enabled);
    
    /**
     * Get clan invites
     */
    List<String> getClanInvites(String clanName);
    
    /**
     * Add clan invite
     */
    void addClanInvite(String clanName, String playerName);
    
    /**
     * Remove clan invite
     */
    void removeClanInvite(String clanName, String playerName);
    
    /**
     * Check if player is invited to clan
     */
    boolean isPlayerInvitedToClan(String playerName, String clanName);
    
    /**
     * Get all clan invites for a player
     */
    List<String> getPlayerInvites(String playerName);
    
    /**
     * Get pending alliances
     */
    List<String> getPendingAlliances(String clanName);
    
    /**
     * Add pending alliance
     */
    void addPendingAlliance(String clan1, String clan2);
    
    /**
     * Remove pending alliance
     */
    void removePendingAlliance(String clan1, String clan2);
    
    /**
     * Get clan reports
     */
    List<Map<String, Object>> getClanReports(String clanName);
    
    /**
     * Add clan report
     */
    void addClanReport(String clanName, String reporter, String reason, long timestamp);
    
    /**
     * Get player clan history
     */
    List<Map<String, Object>> getPlayerClanHistory(String playerName);
    
    /**
     * Add player clan history entry
     */
    void addPlayerClanHistory(String playerName, String clanName, String action, long timestamp);
    
    /**
     * Reload cache (if applicable)
     */
    void reloadCache();
    
    /**
     * Get cache statistics
     */
    Map<String, Object> getCacheStats();
    
    /**
     * Get cached player clan
     */
    String getCachedPlayerClan(String playerName);
    
    /**
     * Get cached clan names
     */
    Set<String> getCachedClanNames();
    
    /**
     * Get kill/death ratio for a player
     */
    double getKillDeathRatio(String playerName);
    
    /**
     * Fix clan colors (for data integrity)
     */
    void fixClanColorsAsync(CommandSender sender);
    
    /**
     * Repair clan leadership
     */
    void repairClanLeadership(CommandSender sender);
    
    /**
     * Migrate data from another storage provider
     */
    void migrateFrom(StorageProvider source) throws Exception;
    
    /**
     * Export data to YAML format
     */
    Map<String, Object> exportToYaml();
    
    /**
     * Import data from YAML format
     */
    void importFromYaml(Map<String, Object> data);
    
    /**
     * Get DataSource (for SQL-based storage providers)
     */
    javax.sql.DataSource getDataSource();
    
        /**
     * Get clan member count
     */
    int getClanMemberCount(String clanName);

    /**
     * Check if friendly fire is enabled for allies
     */
    boolean isFriendlyFireAlliesEnabled(String clanName);

    /**
     * Check if clans are allied
     */
    boolean areClansAllied(String clan1, String clan2);

    /**
     * Increment player kills
     */
    void incrementPlayerKills(String playerName);

    /**
     * Increment player deaths
     */
    void incrementPlayerDeaths(String playerName);

    /**
     * Check if friendly fire is enabled for a clan
     */
    boolean isFriendlyFireEnabled(String clanName);

    /**
     * Set friendly fire enabled for a clan
     */
    void setFriendlyFireEnabled(String clanName, boolean enabled);

} 
