package me.lewisainsworth.vanguardclans.Database;

import me.lewisainsworth.vanguardclans.VanguardClan;
import me.lewisainsworth.vanguardclans.Utils.FileHandler;
import me.lewisainsworth.vanguardclans.Utils.MSG;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class YamlStorageProvider extends AbstractStorageProvider {
    
    private final File dataFile;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private FileConfiguration data;
    
    public YamlStorageProvider(FileConfiguration config) {
        super(config);
        this.dataFile = new File(plugin.getDataFolder(), "clans_data.yml");
        this.data = new YamlConfiguration();
    }
    
    @Override
    public void initialize() throws Exception {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            dataFile.createNewFile();
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        reloadCache();
    }
    
    @Override
    public void close() {
        saveData();
    }

    @Override
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        throw new UnsupportedOperationException("YAML storage provider does not support SQL connections");
    }
    
    @Override
    public void setupTables() throws Exception {
        // No tables needed for YAML storage
        reloadCache();
    }
    
    @Override
    public String getClanLeader(String clanName) {
        lock.readLock().lock();
        try {
            ConfigurationSection clanSection = data.getConfigurationSection("clans." + clanName);
            return clanSection != null ? clanSection.getString("leader") : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public String getClanFounder(String clanName) {
        lock.readLock().lock();
        try {
            ConfigurationSection clanSection = data.getConfigurationSection("clans." + clanName);
            return clanSection != null ? clanSection.getString("founder") : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public double getClanMoney(String clanName) {
        lock.readLock().lock();
        try {
            ConfigurationSection clanSection = data.getConfigurationSection("clans." + clanName);
            return clanSection != null ? clanSection.getDouble("money", 0.0) : 0.0;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void setClanMoney(String clanName, double amount) {
        lock.writeLock().lock();
        try {
            data.set("clans." + clanName + ".money", amount);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public String getClanPrivacy(String clanName) {
        lock.readLock().lock();
        try {
            ConfigurationSection clanSection = data.getConfigurationSection("clans." + clanName);
            return clanSection != null ? clanSection.getString("privacy", "public") : "public";
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void setClanPrivacy(String clanName, String privacy) {
        lock.writeLock().lock();
        try {
            data.set("clans." + clanName + ".privacy", privacy);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public String getClanColoredName(String clanName) {
        lock.readLock().lock();
        try {
            ConfigurationSection clanSection = data.getConfigurationSection("clans." + clanName);
            return clanSection != null ? clanSection.getString("name_colored", clanName) : clanName;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void createClan(String clanName, String coloredName, String founder, String leader, double money, String privacy) {
        lock.writeLock().lock();
        try {
            String path = "clans." + clanName;
            data.set(path + ".name", clanName);
            data.set(path + ".name_colored", coloredName);
            data.set(path + ".founder", founder);
            data.set(path + ".leader", leader);
            data.set(path + ".money", money);
            data.set(path + ".privacy", privacy);
            data.set(path + ".created", System.currentTimeMillis());
            
            // Add leader as first member
            data.set("clan_users." + clanName + "." + leader, true);
            
            saveData();
            reloadCache();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void deleteClan(String clanName) {
        lock.writeLock().lock();
        try {
            data.set("clans." + clanName, null);
            data.set("clan_users." + clanName, null);
            data.set("clan_homes." + clanName, null);
            data.set("friendlyfire." + clanName, null);
            data.set("clan_invites." + clanName, null);
            data.set("alliances." + clanName, null);
            data.set("pending_alliances." + clanName, null);
            data.set("reports." + clanName, null);
            
            saveData();
            reloadCache();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void addPlayerToClan(String playerName, String clanName) {
        lock.writeLock().lock();
        try {
            data.set("clan_users." + clanName + "." + playerName, true);
            saveData();
            reloadCache();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void removePlayerFromClan(String playerName, String clanName) {
        lock.writeLock().lock();
        try {
            data.set("clan_users." + clanName + "." + playerName, null);
            saveData();
            reloadCache();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void updateClanLeader(String clanName, String newLeader) {
        lock.writeLock().lock();
        try {
            data.set("clans." + clanName + ".leader", newLeader);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void updateClanName(String oldName, String newName, String newColoredName) {
        lock.writeLock().lock();
        try {
            // Get all data for the old clan
            ConfigurationSection oldClanSection = data.getConfigurationSection("clans." + oldName);
            if (oldClanSection == null) return;
            
            // Create new clan with new name
            data.set("clans." + newName, oldClanSection);
            data.set("clans." + newName + ".name", newName);
            data.set("clans." + newName + ".name_colored", newColoredName);
            
            // Update clan_users
            ConfigurationSection oldUsersSection = data.getConfigurationSection("clan_users." + oldName);
            if (oldUsersSection != null) {
                data.set("clan_users." + newName, oldUsersSection);
                data.set("clan_users." + oldName, null);
            }
            
            // Update other related data
            updateRelatedData(oldName, newName);
            
            // Remove old clan
            data.set("clans." + oldName, null);
            
            saveData();
            reloadCache();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void updateRelatedData(String oldName, String newName) {
        // Update clan homes
        if (data.contains("clan_homes." + oldName)) {
            data.set("clan_homes." + newName, data.get("clan_homes." + oldName));
            data.set("clan_homes." + oldName, null);
        }
        
        // Update friendly fire
        if (data.contains("friendlyfire." + oldName)) {
            data.set("friendlyfire." + newName, data.get("friendlyfire." + oldName));
            data.set("friendlyfire." + oldName, null);
        }
        
        // Update invites
        if (data.contains("clan_invites." + oldName)) {
            data.set("clan_invites." + newName, data.get("clan_invites." + oldName));
            data.set("clan_invites." + oldName, null);
        }
        
        // Update alliances
        if (data.contains("alliances." + oldName)) {
            data.set("alliances." + newName, data.get("alliances." + oldName));
            data.set("alliances." + oldName, null);
        }
        
        // Update pending alliances
        if (data.contains("pending_alliances." + oldName)) {
            data.set("pending_alliances." + newName, data.get("pending_alliances." + oldName));
            data.set("pending_alliances." + oldName, null);
        }
        
        // Update reports
        if (data.contains("reports." + oldName)) {
            data.set("reports." + newName, data.get("reports." + oldName));
            data.set("reports." + oldName, null);
        }
    }
    
    @Override
    public void reloadCache() {
        lock.writeLock().lock();
        try {
            playerClanCache.clear();
            clanNamesCache.clear();
            clanColoredNameCache.clear();
            
            // Load clan names and colored names
            ConfigurationSection clansSection = data.getConfigurationSection("clans");
            if (clansSection != null) {
                for (String clanName : clansSection.getKeys(false)) {
                    clanNamesCache.add(clanName.toLowerCase());
                    String coloredName = data.getString("clans." + clanName + ".name_colored", clanName);
                    clanColoredNameCache.put(clanName.toLowerCase(), coloredName);
                }
            }
            
            // Load player-clan relationships
            ConfigurationSection usersSection = data.getConfigurationSection("clan_users");
            if (usersSection != null) {
                for (String clanName : usersSection.getKeys(false)) {
                    ConfigurationSection clanUsersSection = usersSection.getConfigurationSection(clanName);
                    if (clanUsersSection != null) {
                        for (String playerName : clanUsersSection.getKeys(false)) {
                            playerClanCache.put(playerName.toLowerCase(), clanName);
                        }
                    }
                }
            }
            
            lastCacheUpdate = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void migrateFrom(StorageProvider source) throws Exception {
        lock.writeLock().lock();
        try {
            // Migrate clans
            Set<String> clans = source.getAllClans();
            for (String clanName : clans) {
                String coloredName = source.getClanColoredName(clanName);
                String founder = source.getClanFounder(clanName);
                String leader = source.getClanLeader(clanName);
                double money = source.getClanMoney(clanName);
                String privacy = source.getClanPrivacy(clanName);
                
                createClan(clanName, coloredName, founder, leader, money, privacy);
                
                // Migrate members
                List<String> members = source.getClanMembers(clanName);
                for (String member : members) {
                    if (!member.equals(leader)) {
                        addPlayerToClan(member, clanName);
                    }
                }
                
                // Migrate home
                Location home = source.getClanHome(clanName);
                if (home != null) {
                    setClanHome(clanName, home);
                }
                
                // Migrate friendly fire setting
                boolean friendlyFire = source.getFriendlyFire(clanName);
                setFriendlyFire(clanName, friendlyFire);
                
                // Migrate invites
                List<String> invites = source.getClanInvites(clanName);
                for (String invite : invites) {
                    addClanInvite(clanName, invite);
                }
                
                // Migrate alliances
                List<String> alliances = source.getClanAlliances(clanName);
                for (String alliance : alliances) {
                    // Note: This is simplified, you might want to handle alliance data more carefully
                    addPendingAlliance(clanName, alliance);
                }
            }
            
            saveData();
            reloadCache();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Map<String, Object> exportToYaml() {
        lock.readLock().lock();
        try {
            Map<String, Object> export = new HashMap<>();
            export.put("clans", data.getConfigurationSection("clans"));
            export.put("clan_users", data.getConfigurationSection("clan_users"));
            export.put("clan_homes", data.getConfigurationSection("clan_homes"));
            export.put("friendlyfire", data.getConfigurationSection("friendlyfire"));
            export.put("clan_invites", data.getConfigurationSection("clan_invites"));
            export.put("alliances", data.getConfigurationSection("alliances"));
            export.put("pending_alliances", data.getConfigurationSection("pending_alliances"));
            export.put("reports", data.getConfigurationSection("reports"));
            export.put("player_clan_history", data.getConfigurationSection("player_clan_history"));
            return export;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void importFromYaml(Map<String, Object> data) {
        lock.writeLock().lock();
        try {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                this.data.set(entry.getKey(), entry.getValue());
            }
            saveData();
            reloadCache();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // Implementation of abstract methods
    @Override
    protected List<String> getClanMembersImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            List<String> members = new ArrayList<>();
            ConfigurationSection usersSection = data.getConfigurationSection("clan_users." + clanName);
            if (usersSection != null) {
                members.addAll(usersSection.getKeys(false));
            }
            return members;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected boolean isClanBannedImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            ConfigurationSection bannedSection = data.getConfigurationSection("banned_clans");
            return bannedSection != null && bannedSection.contains(clanName);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected List<String> getPlayerInvitesImpl(String playerName) throws Exception {
        lock.readLock().lock();
        try {
            List<String> invites = new ArrayList<>();
            ConfigurationSection invitesSection = data.getConfigurationSection("clan_invites");
            if (invitesSection != null) {
                for (String clanName : invitesSection.getKeys(false)) {
                    ConfigurationSection clanInvites = invitesSection.getConfigurationSection(clanName);
                    if (clanInvites != null && clanInvites.contains(playerName)) {
                        invites.add(clanName);
                    }
                }
            }
            return invites;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected int getClanMemberCountImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            ConfigurationSection membersSection = data.getConfigurationSection("clan_users");
            if (membersSection != null) {
                int count = 0;
                for (String username : membersSection.getKeys(false)) {
                    String clan = membersSection.getString(username);
                    if (clanName.equalsIgnoreCase(clan)) {
                        count++;
                    }
                }
                return count;
            }
            return 0;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected boolean isFriendlyFireAlliesEnabledImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            ConfigurationSection ffSection = data.getConfigurationSection("friendlyfire_allies");
            return ffSection != null && ffSection.getBoolean(clanName, false);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected boolean areClansAlliedImpl(String clan1, String clan2) throws Exception {
        lock.readLock().lock();
        try {
            ConfigurationSection alliancesSection = data.getConfigurationSection("alliances");
            if (alliancesSection != null) {
                for (String key : alliancesSection.getKeys(false)) {
                    ConfigurationSection alliance = alliancesSection.getConfigurationSection(key);
                    if (alliance != null) {
                        String c1 = alliance.getString("clan1");
                        String c2 = alliance.getString("clan2");
                        if ((clan1.equalsIgnoreCase(c1) && clan2.equalsIgnoreCase(c2)) ||
                            (clan1.equalsIgnoreCase(c2) && clan2.equalsIgnoreCase(c1))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected void incrementPlayerKillsImpl(String playerName) throws Exception {
        lock.writeLock().lock();
        try {
            ConfigurationSection statsSection = data.getConfigurationSection("player_stats");
            if (statsSection == null) {
                statsSection = data.createSection("player_stats");
            }
            
            ConfigurationSection playerSection = statsSection.getConfigurationSection(playerName);
            if (playerSection == null) {
                playerSection = statsSection.createSection(playerName);
                playerSection.set("kills", 1);
                playerSection.set("deaths", 0);
            } else {
                int currentKills = playerSection.getInt("kills", 0);
                playerSection.set("kills", currentKills + 1);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected void incrementPlayerDeathsImpl(String playerName) throws Exception {
        lock.writeLock().lock();
        try {
            ConfigurationSection statsSection = data.getConfigurationSection("player_stats");
            if (statsSection == null) {
                statsSection = data.createSection("player_stats");
            }
            
            ConfigurationSection playerSection = statsSection.getConfigurationSection(playerName);
            if (playerSection == null) {
                playerSection = statsSection.createSection(playerName);
                playerSection.set("kills", 0);
                playerSection.set("deaths", 1);
            } else {
                int currentDeaths = playerSection.getInt("deaths", 0);
                playerSection.set("deaths", currentDeaths + 1);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected Location getClanHomeImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            String locationString = data.getString("clan_homes." + clanName);
            return parseLocation(locationString);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected void setClanHomeImpl(String clanName, Location location) throws Exception {
        lock.writeLock().lock();
        try {
            String locationString = serializeLocation(location);
            data.set("clan_homes." + clanName, locationString);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected List<String> getClanAlliancesImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            List<String> alliances = new ArrayList<>();
            ConfigurationSection alliancesSection = data.getConfigurationSection("alliances." + clanName);
            if (alliancesSection != null) {
                alliances.addAll(alliancesSection.getKeys(false));
            }
            return alliances;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected void createAllianceImpl(String clan1, String clan2, boolean friendlyFire) throws Exception {
        lock.writeLock().lock();
        try {
            data.set("alliances." + clan1 + "." + clan2 + ".friendly_fire", friendlyFire);
            data.set("alliances." + clan2 + "." + clan1 + ".friendly_fire", friendlyFire);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected void removeAllianceImpl(String clan1, String clan2) throws Exception {
        lock.writeLock().lock();
        try {
            data.set("alliances." + clan1 + "." + clan2, null);
            data.set("alliances." + clan2 + "." + clan1, null);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected boolean getFriendlyFireImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            return data.getBoolean("friendlyfire." + clanName + ".enabled", false);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected void setFriendlyFireImpl(String clanName, boolean enabled) throws Exception {
        lock.writeLock().lock();
        try {
            data.set("friendlyfire." + clanName + ".enabled", enabled);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected List<String> getClanInvitesImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            List<String> invites = new ArrayList<>();
            ConfigurationSection invitesSection = data.getConfigurationSection("clan_invites." + clanName);
            if (invitesSection != null) {
                invites.addAll(invitesSection.getKeys(false));
            }
            return invites;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected void addClanInviteImpl(String clanName, String playerName) throws Exception {
        lock.writeLock().lock();
        try {
            data.set("clan_invites." + clanName + "." + playerName, System.currentTimeMillis());
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected void removeClanInviteImpl(String clanName, String playerName) throws Exception {
        lock.writeLock().lock();
        try {
            data.set("clan_invites." + clanName + "." + playerName, null);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected boolean isPlayerInvitedToClanImpl(String playerName, String clanName) throws Exception {
        lock.readLock().lock();
        try {
            return data.contains("clan_invites." + clanName + "." + playerName);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected List<String> getPendingAlliancesImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            List<String> pending = new ArrayList<>();
            ConfigurationSection pendingSection = data.getConfigurationSection("pending_alliances." + clanName);
            if (pendingSection != null) {
                pending.addAll(pendingSection.getKeys(false));
            }
            return pending;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected void addPendingAllianceImpl(String clan1, String clan2) throws Exception {
        lock.writeLock().lock();
        try {
            data.set("pending_alliances." + clan1 + "." + clan2, System.currentTimeMillis());
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected void removePendingAllianceImpl(String clan1, String clan2) throws Exception {
        lock.writeLock().lock();
        try {
            data.set("pending_alliances." + clan1 + "." + clan2, null);
            data.set("pending_alliances." + clan2 + "." + clan1, null);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected List<Map<String, Object>> getClanReportsImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            List<Map<String, Object>> reports = new ArrayList<>();
            ConfigurationSection reportsSection = data.getConfigurationSection("reports." + clanName);
            if (reportsSection != null) {
                for (String reportId : reportsSection.getKeys(false)) {
                    Map<String, Object> report = new HashMap<>();
                    report.put("id", reportId);
                    report.put("reporter", reportsSection.getString(reportId + ".reporter"));
                    report.put("reason", reportsSection.getString(reportId + ".reason"));
                    report.put("timestamp", reportsSection.getLong(reportId + ".timestamp"));
                    reports.add(report);
                }
            }
            return reports;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected void addClanReportImpl(String clanName, String reporter, String reason, long timestamp) throws Exception {
        lock.writeLock().lock();
        try {
            String reportId = UUID.randomUUID().toString();
            data.set("reports." + clanName + "." + reportId + ".reporter", reporter);
            data.set("reports." + clanName + "." + reportId + ".reason", reason);
            data.set("reports." + clanName + "." + reportId + ".timestamp", timestamp);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected List<Map<String, Object>> getPlayerClanHistoryImpl(String playerName) throws Exception {
        lock.readLock().lock();
        try {
            List<Map<String, Object>> history = new ArrayList<>();
            ConfigurationSection historySection = data.getConfigurationSection("player_clan_history." + playerName);
            if (historySection != null) {
                for (String entryId : historySection.getKeys(false)) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", entryId);
                    entry.put("clan", historySection.getString(entryId + ".clan"));
                    entry.put("action", historySection.getString(entryId + ".action"));
                    entry.put("timestamp", historySection.getLong(entryId + ".timestamp"));
                    history.add(entry);
                }
            }
            return history;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    protected void addPlayerClanHistoryImpl(String playerName, String clanName, String action, long timestamp) throws Exception {
        lock.writeLock().lock();
        try {
            String entryId = UUID.randomUUID().toString();
            data.set("player_clan_history." + playerName + "." + entryId + ".clan", clanName);
            data.set("player_clan_history." + playerName + "." + entryId + ".action", action);
            data.set("player_clan_history." + playerName + "." + entryId + ".timestamp", timestamp);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    protected void fixClanColorsImpl(CommandSender sender) throws Exception {
        // YAML storage doesn't need color fixing as it stores both plain and colored names
        Bukkit.getScheduler().runTask(plugin, () ->
            sender.sendMessage(MSG.color(langManager.getMessage("msg.admin_fix_success").replace("{count}", "0")))
        );
    }
    
    @Override
    protected void repairClanLeadershipImpl(CommandSender sender) throws Exception {
        int fixed = 0;
        
        lock.writeLock().lock();
        try {
            ConfigurationSection clansSection = data.getConfigurationSection("clans");
            if (clansSection != null) {
                for (String clanName : clansSection.getKeys(false)) {
                    String leader = data.getString("clans." + clanName + ".leader");
                    if (leader != null && !data.contains("clan_users." + clanName + "." + leader)) {
                        // Leader is not in clan_users, find a new leader
                        ConfigurationSection usersSection = data.getConfigurationSection("clan_users." + clanName);
                        if (usersSection != null && !usersSection.getKeys(false).isEmpty()) {
                            String newLeader = usersSection.getKeys(false).iterator().next();
                            data.set("clans." + clanName + ".leader", newLeader);
                            fixed++;
                        }
                    }
                }
            }
            
            if (fixed > 0) {
                saveData();
                reloadCache();
            }
            
            final int finalFixed = fixed;
            Bukkit.getScheduler().runTask(plugin, () ->
                sender.sendMessage(MSG.color(langManager.getMessage("msg.admin_repair_success").replace("{count}", String.valueOf(finalFixed))))
            );
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error saving YAML data: " + e.getMessage());
        }
    }
    
    @Override
    public javax.sql.DataSource getDataSource() {
        throw new UnsupportedOperationException("YAML storage provider does not support DataSource");
    }

    @Override
    protected boolean isFriendlyFireEnabledImpl(String clanName) throws Exception {
        lock.readLock().lock();
        try {
            ConfigurationSection ffSection = data.getConfigurationSection("friendlyfire");
            if (ffSection == null) return false;
            
            ConfigurationSection clanSection = ffSection.getConfigurationSection(clanName);
            return clanSection != null && clanSection.getBoolean("enabled", false);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    protected void setFriendlyFireEnabledImpl(String clanName, boolean enabled) throws Exception {
        lock.writeLock().lock();
        try {
            ConfigurationSection ffSection = data.getConfigurationSection("friendlyfire");
            if (ffSection == null) {
                ffSection = data.createSection("friendlyfire");
            }

            ConfigurationSection clanSection = ffSection.getConfigurationSection(clanName);
            if (clanSection == null) {
                clanSection = ffSection.createSection(clanName);
            }

            clanSection.set("enabled", enabled);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    protected void updateClanNameImpl(String oldName, String newName, String newColoredName) throws Exception {
        lock.writeLock().lock();
        try {
            // Update clans section
            ConfigurationSection clansSection = data.getConfigurationSection("clans");
            if (clansSection != null && clansSection.contains(oldName)) {
                ConfigurationSection oldClanSection = clansSection.getConfigurationSection(oldName);
                if (oldClanSection != null) {
                    clansSection.set(newName, oldClanSection);
                    clansSection.set(oldName, null);
                    
                    // Update the name and colored name
                    ConfigurationSection newClanSection = clansSection.getConfigurationSection(newName);
                    newClanSection.set("name", newName);
                    newClanSection.set("name_colored", newColoredName);
                }
            }

            // Update clan_users section
            ConfigurationSection usersSection = data.getConfigurationSection("clan_users");
            if (usersSection != null) {
                for (String playerKey : usersSection.getKeys(false)) {
                    ConfigurationSection playerSection = usersSection.getConfigurationSection(playerKey);
                    if (playerSection != null && oldName.equals(playerSection.getString("clan"))) {
                        playerSection.set("clan", newName);
                    }
                }
            }

            // Update other sections that reference clan names
            updateClanNameInSection("friendlyfire", oldName, newName);
            updateClanNameInSection("clan_invites", oldName, newName);
            updateClanNameInSection("reports", oldName, newName);
            updateClanNameInSection("alliances", oldName, newName);
            updateClanNameInSection("pending_alliances", oldName, newName);
            updateClanNameInSection("player_clan_history", oldName, newName);

            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void updateClanNameInSection(String sectionName, String oldName, String newName) {
        ConfigurationSection section = data.getConfigurationSection(sectionName);
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection != null) {
                // Update clan1 and clan2 fields
                if (oldName.equals(itemSection.getString("clan1"))) {
                    itemSection.set("clan1", newName);
                }
                if (oldName.equals(itemSection.getString("clan2"))) {
                    itemSection.set("clan2", newName);
                }
                if (oldName.equals(itemSection.getString("clan"))) {
                    itemSection.set("clan", newName);
                }
                if (oldName.equals(itemSection.getString("requester"))) {
                    itemSection.set("requester", newName);
                }
                if (oldName.equals(itemSection.getString("target"))) {
                    itemSection.set("target", newName);
                }
                if (oldName.equals(itemSection.getString("current_clan"))) {
                    itemSection.set("current_clan", newName);
                }
            }
        }
    }
} 
