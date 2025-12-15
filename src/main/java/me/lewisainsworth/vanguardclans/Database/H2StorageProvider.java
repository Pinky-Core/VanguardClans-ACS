package me.lewisainsworth.vanguardclans.Database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.lewisainsworth.vanguardclans.VanguardClan;
import me.lewisainsworth.vanguardclans.Utils.MSG;
import me.lewisainsworth.vanguardclans.Utils.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;

public class H2StorageProvider extends AbstractStorageProvider {
    
    private HikariDataSource dataSource;
    
    public H2StorageProvider(FileConfiguration config) {
        super(config);
    }
    
    @Override
    public void initialize() throws Exception {
        File dbFile = new File(plugin.getDataFolder(), "clans.h2.db");
        String dbPath = dbFile.getAbsolutePath().replace(".h2.db", "");
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:h2:" + dbPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        hikariConfig.setDriverClassName("me.lewisainsworth.shaded.h2.Driver");
        
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(5);
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setIdleTimeout(300000);
        hikariConfig.setMaxLifetime(1800000);
        
        dataSource = new HikariDataSource(hikariConfig);
        setupTables();
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    
    @Override
    public void setupTables() throws Exception {
        try (Connection con = getConnection(); Statement stmt = con.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clans (
                    name VARCHAR(255) PRIMARY KEY,
                    name_colored TEXT,
                    founder VARCHAR(36),
                    leader VARCHAR(36),
                    money DOUBLE,
                    privacy VARCHAR(12),
                    points INT DEFAULT 0,
                    slot_upgrades INT DEFAULT 0
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clan_users (
                    clan VARCHAR(255),
                    username VARCHAR(36),
                    PRIMARY KEY (clan, username)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS alliances (
                    clan1 VARCHAR(255),
                    clan2 VARCHAR(255),
                    friendly_fire BOOLEAN DEFAULT FALSE,
                    PRIMARY KEY (clan1, clan2)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS friendlyfire (
                    clan VARCHAR(255) PRIMARY KEY,
                    enabled BOOLEAN
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS banned_clans (
                    name VARCHAR(255) PRIMARY KEY,
                    reason TEXT
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS friendlyfire_allies (
                    clan VARCHAR(255) PRIMARY KEY,
                    enabled BOOLEAN
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS economy_players (
                    player VARCHAR(36) PRIMARY KEY,
                    balance DOUBLE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clan_invites (
                    clan VARCHAR(255),
                    username VARCHAR(36),
                    timestamp BIGINT,
                    PRIMARY KEY (clan, username)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pending_alliances (
                    clan1 VARCHAR(255),
                    clan2 VARCHAR(255),
                    timestamp BIGINT,
                    PRIMARY KEY (clan1, clan2)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS reports (
                    id VARCHAR(36) PRIMARY KEY,
                    clan VARCHAR(255),
                    reporter VARCHAR(36),
                    reason TEXT,
                    timestamp BIGINT
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_clan_history (
                    id VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(36),
                    clan VARCHAR(255),
                    action VARCHAR(50),
                    timestamp BIGINT
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clan_homes (
                    clan VARCHAR(255) PRIMARY KEY,
                    world VARCHAR(255),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    yaw FLOAT,
                    pitch FLOAT
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    username VARCHAR(36) PRIMARY KEY,
                    kills INT DEFAULT 0,
                    deaths INT DEFAULT 0
                )
            """);

            ensureColumn(con, "clans", "points", "INT DEFAULT 0");
            ensureColumn(con, "clans", "slot_upgrades", "INT DEFAULT 0");
        }
    }
    
    @Override
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource not initialized");
        }
        return dataSource.getConnection();
    }
    
    @Override
    public String getClanLeader(String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT leader FROM clans WHERE name = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("leader") : null;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting clan leader for " + clanName + ": " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public String getClanFounder(String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT founder FROM clans WHERE name = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("founder") : null;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting clan founder for " + clanName + ": " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public double getClanMoney(String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT money FROM clans WHERE name = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble("money") : 0.0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting clan money for " + clanName + ": " + e.getMessage());
            return 0.0;
        }
    }
    
    @Override
    public void setClanMoney(String clanName, double amount) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clans SET money = ? WHERE name = ?")) {
            ps.setDouble(1, amount);
            ps.setString(2, clanName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting clan money for " + clanName + ": " + e.getMessage());
        }
    }

    @Override
    public int getClanPoints(String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT points FROM clans WHERE name = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("points") : 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting clan points for " + clanName + ": " + e.getMessage());
            return 0;
        }
    }

    @Override
    public void setClanPoints(String clanName, int points) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clans SET points = ? WHERE name = ?")) {
            ps.setInt(1, Math.max(0, points));
            ps.setString(2, clanName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting clan points for " + clanName + ": " + e.getMessage());
        }
    }

    @Override
    public void addClanPoints(String clanName, int delta) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clans SET points = GREATEST(0, points + ?) WHERE name = ?")) {
            ps.setInt(1, delta);
            ps.setString(2, clanName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating clan points for " + clanName + ": " + e.getMessage());
        }
    }

    @Override
    public int getClanSlotUpgrades(String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT slot_upgrades FROM clans WHERE name = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("slot_upgrades") : 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting clan slot upgrades for " + clanName + ": " + e.getMessage());
            return 0;
        }
    }

    @Override
    public void setClanSlotUpgrades(String clanName, int upgrades) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clans SET slot_upgrades = ? WHERE name = ?")) {
            ps.setInt(1, Math.max(0, upgrades));
            ps.setString(2, clanName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting clan slot upgrades for " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public String getClanPrivacy(String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT privacy FROM clans WHERE name = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("privacy") : "public";
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting clan privacy for " + clanName + ": " + e.getMessage());
            return "public";
        }
    }
    
    @Override
    public void setClanPrivacy(String clanName, String privacy) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clans SET privacy = ? WHERE name = ?")) {
            ps.setString(1, privacy);
            ps.setString(2, clanName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting clan privacy for " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public String getClanColoredName(String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT name_colored FROM clans WHERE name = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("name_colored") : clanName;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting clan colored name for " + clanName + ": " + e.getMessage());
            return clanName;
        }
    }
    
    @Override
    public void createClan(String clanName, String coloredName, String founder, String leader, double money, String privacy) {
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Insert clan
                try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO clans (name, name_colored, founder, leader, money, privacy, points, slot_upgrades) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, clanName);
                    ps.setString(2, coloredName);
                    ps.setString(3, founder);
                    ps.setString(4, leader);
                    ps.setDouble(5, money);
                    ps.setString(6, privacy);
                    ps.setInt(7, 0);
                    ps.setInt(8, 0);
                    ps.executeUpdate();
                }
                
                // Add leader as first member
                try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO clan_users (clan, username) VALUES (?, ?)")) {
                    ps.setString(1, clanName);
                    ps.setString(2, leader);
                    ps.executeUpdate();
                }
                
                con.commit();
                reloadCache();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating clan " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public void deleteClan(String clanName) {
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Delete all related data
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM clan_users WHERE clan = ?")) {
                    ps.setString(1, clanName);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM alliances WHERE clan1 = ? OR clan2 = ?")) {
                    ps.setString(1, clanName);
                    ps.setString(2, clanName);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM friendlyfire WHERE clan = ?")) {
                    ps.setString(1, clanName);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM clan_invites WHERE clan = ?")) {
                    ps.setString(1, clanName);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM pending_alliances WHERE clan1 = ? OR clan2 = ?")) {
                    ps.setString(1, clanName);
                    ps.setString(2, clanName);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM reports WHERE clan = ?")) {
                    ps.setString(1, clanName);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM clan_homes WHERE clan = ?")) {
                    ps.setString(1, clanName);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM clans WHERE name = ?")) {
                    ps.setString(1, clanName);
                    ps.executeUpdate();
                }
                
                con.commit();
                reloadCache();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting clan " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public void addPlayerToClan(String playerName, String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO clan_users (clan, username) VALUES (?, ?)")) {
            ps.setString(1, clanName);
            ps.setString(2, playerName);
            ps.executeUpdate();
            reloadCache();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding player " + playerName + " to clan " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public void removePlayerFromClan(String playerName, String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM clan_users WHERE clan = ? AND username = ?")) {
            ps.setString(1, clanName);
            ps.setString(2, playerName);
            ps.executeUpdate();
            reloadCache();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing player " + playerName + " from clan " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public void updateClanLeader(String clanName, String newLeader) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clans SET leader = ? WHERE name = ?")) {
            ps.setString(1, newLeader);
            ps.setString(2, clanName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating clan leader for " + clanName + ": " + e.getMessage());
        }
    }
    
    @Override
    public void updateClanName(String oldName, String newName, String newColoredName) {
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Update clan name
                try (PreparedStatement ps = con.prepareStatement("UPDATE clans SET name = ?, name_colored = ? WHERE name = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, newColoredName);
                    ps.setString(3, oldName);
                    ps.executeUpdate();
                }
                
                // Update all related tables
                String[] tables = {"clan_users", "alliances", "friendlyfire", "clan_invites", "pending_alliances", "reports", "clan_homes"};
                for (String table : tables) {
                    try (PreparedStatement ps = con.prepareStatement("UPDATE " + table + " SET clan = ? WHERE clan = ?")) {
                        ps.setString(1, newName);
                        ps.setString(2, oldName);
                        ps.executeUpdate();
                    }
                }
                
                // Update alliances table (clan1 and clan2 columns)
                try (PreparedStatement ps = con.prepareStatement("UPDATE alliances SET clan1 = ? WHERE clan1 = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = con.prepareStatement("UPDATE alliances SET clan2 = ? WHERE clan2 = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                
                // Update pending_alliances table
                try (PreparedStatement ps = con.prepareStatement("UPDATE pending_alliances SET clan1 = ? WHERE clan1 = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = con.prepareStatement("UPDATE pending_alliances SET clan2 = ? WHERE clan2 = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                
                con.commit();
                reloadCache();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating clan name from " + oldName + " to " + newName + ": " + e.getMessage());
        }
    }
    
    @Override
    public void reloadCache() {
        try (Connection con = getConnection()) {
            playerClanCache.clear();
            clanNamesCache.clear();
            clanColoredNameCache.clear();
            
            // Load clan names and colored names
            try (PreparedStatement ps = con.prepareStatement("SELECT name, name_colored FROM clans")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String clanName = rs.getString("name");
                    String coloredName = rs.getString("name_colored");
                    clanNamesCache.add(clanName.toLowerCase());
                    clanColoredNameCache.put(clanName.toLowerCase(), coloredName != null ? coloredName : clanName);
                }
            }
            
            // Load player-clan relationships
            try (PreparedStatement ps = con.prepareStatement("SELECT clan, username FROM clan_users")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String clanName = rs.getString("clan");
                    String playerName = rs.getString("username");
                    playerClanCache.put(playerName.toLowerCase(), clanName);
                }
            }
            
            lastCacheUpdate = System.currentTimeMillis();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error reloading cache: " + e.getMessage());
        }
    }
    
    @Override
    public void migrateFrom(StorageProvider source) throws Exception {
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Migrate clans
                Set<String> clans = source.getAllClans();
                for (String clanName : clans) {
                    String coloredName = source.getClanColoredName(clanName);
                    String founder = source.getClanFounder(clanName);
                    String leader = source.getClanLeader(clanName);
                    double money = source.getClanMoney(clanName);
                    String privacy = source.getClanPrivacy(clanName);
                    int points = source.getClanPoints(clanName);
                    int upgrades = source.getClanSlotUpgrades(clanName);
                    
                    createClan(clanName, coloredName, founder, leader, money, privacy);
                    setClanPoints(clanName, points);
                    setClanSlotUpgrades(clanName, upgrades);
                    
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
                        createAlliance(clanName, alliance, false);
                    }
                }
                
                con.commit();
                reloadCache();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }
    
    @Override
    public Map<String, Object> exportToYaml() {
        Map<String, Object> export = new HashMap<>();
        try (Connection con = getConnection()) {
            // Export clans
            Map<String, Object> clans = new HashMap<>();
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM clans")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> clan = new HashMap<>();
                    clan.put("name", rs.getString("name"));
                    clan.put("name_colored", rs.getString("name_colored"));
                    clan.put("founder", rs.getString("founder"));
                    clan.put("leader", rs.getString("leader"));
                    clan.put("money", rs.getDouble("money"));
                    clan.put("privacy", rs.getString("privacy"));
                    clan.put("points", rs.getInt("points"));
                    clan.put("slot_upgrades", rs.getInt("slot_upgrades"));
                    clans.put(rs.getString("name"), clan);
                }
            }
            export.put("clans", clans);
            
            // Export clan_users
            Map<String, Object> clanUsers = new HashMap<>();
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM clan_users")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String clanName = rs.getString("clan");
                    String username = rs.getString("username");
                    if (!clanUsers.containsKey(clanName)) {
                        clanUsers.put(clanName, new HashMap<String, Object>());
                    }
                    ((Map<String, Object>) clanUsers.get(clanName)).put(username, true);
                }
            }
            export.put("clan_users", clanUsers);
            
            // Add other exports as needed...
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Error exporting to YAML: " + e.getMessage());
        }
        return export;
    }
    
    @Override
    public void importFromYaml(Map<String, Object> data) {
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Clear existing data
                try (Statement stmt = con.createStatement()) {
                    stmt.executeUpdate("DELETE FROM clans");
                    stmt.executeUpdate("DELETE FROM clan_users");
                    stmt.executeUpdate("DELETE FROM alliances");
                    stmt.executeUpdate("DELETE FROM friendlyfire");
                    stmt.executeUpdate("DELETE FROM clan_invites");
                    stmt.executeUpdate("DELETE FROM pending_alliances");
                    stmt.executeUpdate("DELETE FROM reports");
                    stmt.executeUpdate("DELETE FROM player_clan_history");
                    stmt.executeUpdate("DELETE FROM clan_homes");
                }
                
                // Import clans
                @SuppressWarnings("unchecked")
                Map<String, Object> clans = (Map<String, Object>) data.get("clans");
                if (clans != null) {
                    for (Map.Entry<String, Object> entry : clans.entrySet()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> clan = (Map<String, Object>) entry.getValue();
                        try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO clans (name, name_colored, founder, leader, money, privacy, points, slot_upgrades) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                            ps.setString(1, (String) clan.get("name"));
                            ps.setString(2, (String) clan.get("name_colored"));
                            ps.setString(3, (String) clan.get("founder"));
                            ps.setString(4, (String) clan.get("leader"));
                            ps.setDouble(5, (Double) clan.get("money"));
                            ps.setString(6, (String) clan.get("privacy"));
                            ps.setInt(7, clan.containsKey("points") ? ((Number) clan.get("points")).intValue() : 0);
                            ps.setInt(8, clan.containsKey("slot_upgrades") ? ((Number) clan.get("slot_upgrades")).intValue() : 0);
                            ps.executeUpdate();
                        }
                    }
                }
                
                // Import clan_users
                @SuppressWarnings("unchecked")
                Map<String, Object> clanUsers = (Map<String, Object>) data.get("clan_users");
                if (clanUsers != null) {
                    for (Map.Entry<String, Object> entry : clanUsers.entrySet()) {
                        String clanName = entry.getKey();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> users = (Map<String, Object>) entry.getValue();
                        for (String username : users.keySet()) {
                            try (PreparedStatement ps = con.prepareStatement(
                                "INSERT INTO clan_users (clan, username) VALUES (?, ?)")) {
                                ps.setString(1, clanName);
                                ps.setString(2, username);
                                ps.executeUpdate();
                            }
                        }
                    }
                }
                
                con.commit();
                reloadCache();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error importing from YAML: " + e.getMessage());
        }
    }
    
    // Implementation of abstract methods
    @Override
    protected List<String> getClanMembersImpl(String clanName) throws Exception {
        List<String> members = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT username FROM clan_users WHERE clan = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(rs.getString("username"));
            }
        }
        return members;
    }
    
    @Override
    protected boolean isClanBannedImpl(String clanName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT name FROM banned_clans WHERE name = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }
    
    @Override
    protected List<String> getPlayerInvitesImpl(String playerName) throws Exception {
        List<String> invites = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT clan FROM clan_invites WHERE username = ?")) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                invites.add(rs.getString("clan"));
            }
        }
        return invites;
    }
    
    @Override
    protected int getClanMemberCountImpl(String clanName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM clan_users WHERE clan = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    
    @Override
    protected boolean isFriendlyFireAlliesEnabledImpl(String clanName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT enabled FROM friendlyfire_allies WHERE clan = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getBoolean("enabled");
        }
    }
    
    @Override
    protected boolean areClansAlliedImpl(String clan1, String clan2) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM alliances WHERE (clan1 = ? AND clan2 = ?) OR (clan1 = ? AND clan2 = ?)")) {
            ps.setString(1, clan1);
            ps.setString(2, clan2);
            ps.setString(3, clan2);
            ps.setString(4, clan1);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }
    
    @Override
    protected void incrementPlayerKillsImpl(String playerName) throws Exception {
        try (Connection con = getConnection()) {
            try (PreparedStatement checkPs = con.prepareStatement("SELECT username FROM player_stats WHERE username = ?")) {
                checkPs.setString(1, playerName);
                ResultSet rs = checkPs.executeQuery();
                if (rs.next()) {
                    try (PreparedStatement updatePs = con.prepareStatement("UPDATE player_stats SET kills = kills + 1 WHERE username = ?")) {
                        updatePs.setString(1, playerName);
                        updatePs.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insertPs = con.prepareStatement("INSERT INTO player_stats (username, kills, deaths) VALUES (?, 1, 0)")) {
                        insertPs.setString(1, playerName);
                        insertPs.executeUpdate();
                    }
                }
            }
        }
    }
    
    @Override
    protected void incrementPlayerDeathsImpl(String playerName) throws Exception {
        try (Connection con = getConnection()) {
            try (PreparedStatement checkPs = con.prepareStatement("SELECT username FROM player_stats WHERE username = ?")) {
                checkPs.setString(1, playerName);
                ResultSet rs = checkPs.executeQuery();
                if (rs.next()) {
                    try (PreparedStatement updatePs = con.prepareStatement("UPDATE player_stats SET deaths = deaths + 1 WHERE username = ?")) {
                        updatePs.setString(1, playerName);
                        updatePs.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insertPs = con.prepareStatement("INSERT INTO player_stats (username, kills, deaths) VALUES (?, 0, 1)")) {
                        insertPs.setString(1, playerName);
                        insertPs.executeUpdate();
                    }
                }
            }
        }
    }
    
    @Override
    protected Location getClanHomeImpl(String clanName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT world, x, y, z, yaw, pitch FROM clan_homes WHERE clan = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String worldName = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");
                
                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    return new Location(world, x, y, z, yaw, pitch);
                }
            }
        }
        return null;
    }
    
    @Override
    protected void setClanHomeImpl(String clanName, Location location) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "INSERT INTO clan_homes (clan, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                 "ON DUPLICATE KEY UPDATE world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?")) {
            ps.setString(1, clanName);
            ps.setString(2, location.getWorld().getName());
            ps.setDouble(3, location.getX());
            ps.setDouble(4, location.getY());
            ps.setDouble(5, location.getZ());
            ps.setFloat(6, location.getYaw());
            ps.setFloat(7, location.getPitch());
            ps.setString(8, location.getWorld().getName());
            ps.setDouble(9, location.getX());
            ps.setDouble(10, location.getY());
            ps.setDouble(11, location.getZ());
            ps.setFloat(12, location.getYaw());
            ps.setFloat(13, location.getPitch());
            ps.executeUpdate();
        }
    }
    
    @Override
    protected List<String> getClanAlliancesImpl(String clanName) throws Exception {
        List<String> alliances = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT clan2 FROM alliances WHERE clan1 = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                alliances.add(rs.getString("clan2"));
            }
        }
        return alliances;
    }
    
    @Override
    protected void createAllianceImpl(String clan1, String clan2, boolean friendlyFire) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO alliances (clan1, clan2, friendly_fire) VALUES (?, ?, ?)")) {
            ps.setString(1, clan1);
            ps.setString(2, clan2);
            ps.setBoolean(3, friendlyFire);
            ps.executeUpdate();
        }
    }
    
    @Override
    protected void removeAllianceImpl(String clan1, String clan2) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM alliances WHERE (clan1 = ? AND clan2 = ?) OR (clan1 = ? AND clan2 = ?)")) {
            ps.setString(1, clan1);
            ps.setString(2, clan2);
            ps.setString(3, clan2);
            ps.setString(4, clan1);
            ps.executeUpdate();
        }
    }
    
    @Override
    protected boolean getFriendlyFireImpl(String clanName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT enabled FROM friendlyfire WHERE clan = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getBoolean("enabled");
        }
    }
    
    @Override
    protected void setFriendlyFireImpl(String clanName, boolean enabled) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO friendlyfire (clan, enabled) VALUES (?, ?) ON DUPLICATE KEY UPDATE enabled = ?")) {
            ps.setString(1, clanName);
            ps.setBoolean(2, enabled);
            ps.setBoolean(3, enabled);
            ps.executeUpdate();
        }
    }
    
    @Override
    protected List<String> getClanInvitesImpl(String clanName) throws Exception {
        List<String> invites = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT username FROM clan_invites WHERE clan = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                invites.add(rs.getString("username"));
            }
        }
        return invites;
    }
    
    @Override
    protected void addClanInviteImpl(String clanName, String playerName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO clan_invites (clan, username, timestamp) VALUES (?, ?, ?)")) {
            ps.setString(1, clanName);
            ps.setString(2, playerName);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
    
    @Override
    protected void removeClanInviteImpl(String clanName, String playerName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM clan_invites WHERE clan = ? AND username = ?")) {
            ps.setString(1, clanName);
            ps.setString(2, playerName);
            ps.executeUpdate();
        }
    }
    
    @Override
    protected boolean isPlayerInvitedToClanImpl(String playerName, String clanName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM clan_invites WHERE clan = ? AND username = ?")) {
            ps.setString(1, clanName);
            ps.setString(2, playerName);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }
    
    @Override
    protected List<String> getPendingAlliancesImpl(String clanName) throws Exception {
        List<String> pending = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT clan2 FROM pending_alliances WHERE clan1 = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                pending.add(rs.getString("clan2"));
            }
        }
        return pending;
    }
    
    @Override
    protected void addPendingAllianceImpl(String clan1, String clan2) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO pending_alliances (clan1, clan2, timestamp) VALUES (?, ?, ?)")) {
            ps.setString(1, clan1);
            ps.setString(2, clan2);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
    
    @Override
    protected void removePendingAllianceImpl(String clan1, String clan2) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM pending_alliances WHERE (clan1 = ? AND clan2 = ?) OR (clan1 = ? AND clan2 = ?)")) {
            ps.setString(1, clan1);
            ps.setString(2, clan2);
            ps.setString(3, clan2);
            ps.setString(4, clan1);
            ps.executeUpdate();
        }
    }
    
    @Override
    protected List<Map<String, Object>> getClanReportsImpl(String clanName) throws Exception {
        List<Map<String, Object>> reports = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT id, reporter, reason, timestamp FROM reports WHERE clan = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> report = new HashMap<>();
                report.put("id", rs.getString("id"));
                report.put("reporter", rs.getString("reporter"));
                report.put("reason", rs.getString("reason"));
                report.put("timestamp", rs.getLong("timestamp"));
                reports.add(report);
            }
        }
        return reports;
    }
    
    @Override
    protected void addClanReportImpl(String clanName, String reporter, String reason, long timestamp) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO reports (id, clan, reporter, reason, timestamp) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, clanName);
            ps.setString(3, reporter);
            ps.setString(4, reason);
            ps.setLong(5, timestamp);
            ps.executeUpdate();
        }
    }
    
    @Override
    protected List<Map<String, Object>> getPlayerClanHistoryImpl(String playerName) throws Exception {
        List<Map<String, Object>> history = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT id, clan, action, timestamp FROM player_clan_history WHERE username = ?")) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", rs.getString("id"));
                entry.put("clan", rs.getString("clan"));
                entry.put("action", rs.getString("action"));
                entry.put("timestamp", rs.getLong("timestamp"));
                history.add(entry);
            }
        }
        return history;
    }
    
    @Override
    protected void addPlayerClanHistoryImpl(String playerName, String clanName, String action, long timestamp) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO player_clan_history (id, username, clan, action, timestamp) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, playerName);
            ps.setString(3, clanName);
            ps.setString(4, action);
            ps.setLong(5, timestamp);
            ps.executeUpdate();
        }
    }
    
    @Override
    protected void fixClanColorsImpl(CommandSender sender) throws Exception {
        final int[] fixed = {0};
        final LangManager finalLangManager = langManager;
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Fix name_colored inconsistencies
                try (PreparedStatement ps = con.prepareStatement("UPDATE clans SET name_colored = name WHERE name_colored IS NULL")) {
                    fixed[0] += ps.executeUpdate();
                }
                
                // Update references in other tables
                String[] tables = {"clan_users", "friendlyfire", "clan_invites", "reports", "alliances", "pending_alliances", "player_clan_history"};
                for (String table : tables) {
                    try (PreparedStatement ps = con.prepareStatement("UPDATE " + table + " SET clan = (SELECT name FROM clans WHERE name = " + table + ".clan)")) {
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        // Ignore if table doesn't exist or column doesn't exist
                    }
                }
                
                con.commit();
                reloadCache();
                
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(MSG.color(finalLangManager.getMessage("msg.admin_fix_success").replace("{count}", String.valueOf(fixed[0]))))
                );
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }
    
    @Override
    protected void repairClanLeadershipImpl(CommandSender sender) throws Exception {
        final int[] fixed = {0};
        final LangManager finalLangManager = langManager;
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Find clans where leader is not in clan_users
                try (PreparedStatement ps = con.prepareStatement(
                    "SELECT c.name, c.leader FROM clans c LEFT JOIN clan_users cu ON c.name = cu.clan AND c.leader = cu.username " +
                    "WHERE cu.username IS NULL")) {
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        String clanName = rs.getString("name");
                        String oldLeader = rs.getString("leader");
                        
                        // Find a new leader
                        try (PreparedStatement findLeader = con.prepareStatement("SELECT username FROM clan_users WHERE clan = ? LIMIT 1")) {
                            findLeader.setString(1, clanName);
                            ResultSet leaderRs = findLeader.executeQuery();
                            if (leaderRs.next()) {
                                String newLeader = leaderRs.getString("username");
                                try (PreparedStatement updateLeader = con.prepareStatement("UPDATE clans SET leader = ? WHERE name = ?")) {
                                    updateLeader.setString(1, newLeader);
                                    updateLeader.setString(2, clanName);
                                    updateLeader.executeUpdate();
                                    fixed[0]++;
                                }
                            }
                        }
                    }
                }
                
                con.commit();
                reloadCache();
                
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(MSG.color(finalLangManager.getMessage("msg.admin_repair_success").replace("{count}", String.valueOf(fixed[0]))))
                );
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private void ensureColumn(Connection con, String table, String column, String definition) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            if (!rs.next()) {
                try (Statement alter = con.createStatement()) {
                    alter.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                }
            }
        }
    }
    
    @Override
    public javax.sql.DataSource getDataSource() {
        return dataSource;
    }

    @Override
    protected boolean isFriendlyFireEnabledImpl(String clanName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT enabled FROM friendlyfire WHERE clan = ?")) {
            ps.setString(1, clanName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("enabled");
            }
        }
    }

    @Override
    protected void setFriendlyFireEnabledImpl(String clanName, boolean enabled) throws Exception {
        try (Connection con = getConnection()) {
            // Check if entry exists
            try (PreparedStatement checkStmt = con.prepareStatement("SELECT clan FROM friendlyfire WHERE clan = ?")) {
                checkStmt.setString(1, clanName);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        // Update existing entry
                        try (PreparedStatement updateStmt = con.prepareStatement("UPDATE friendlyfire SET enabled = ? WHERE clan = ?")) {
                            updateStmt.setBoolean(1, enabled);
                            updateStmt.setString(2, clanName);
                            updateStmt.executeUpdate();
                        }
                    } else {
                        // Insert new entry
                        try (PreparedStatement insertStmt = con.prepareStatement("INSERT INTO friendlyfire (clan, enabled) VALUES (?, ?)")) {
                            insertStmt.setString(1, clanName);
                            insertStmt.setBoolean(2, enabled);
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void updateClanNameImpl(String oldName, String newName, String newColoredName) throws Exception {
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Update clans table
                try (PreparedStatement ps = con.prepareStatement("UPDATE clans SET name = ?, name_colored = ? WHERE name = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, newColoredName);
                    ps.setString(3, oldName);
                    ps.executeUpdate();
                }

                // Update clan_users table
                try (PreparedStatement ps = con.prepareStatement("UPDATE clan_users SET clan = ? WHERE clan = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                // Update friendlyfire table
                try (PreparedStatement ps = con.prepareStatement("UPDATE friendlyfire SET clan = ? WHERE clan = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                // Update clan_invites table
                try (PreparedStatement ps = con.prepareStatement("UPDATE clan_invites SET clan = ? WHERE clan = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                // Update reports table
                try (PreparedStatement ps = con.prepareStatement("UPDATE reports SET clan = ? WHERE clan = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                // Update alliances table
                try (PreparedStatement ps = con.prepareStatement("UPDATE alliances SET clan1 = ? WHERE clan1 = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("UPDATE alliances SET clan2 = ? WHERE clan2 = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                // Update pending_alliances table
                try (PreparedStatement ps = con.prepareStatement("UPDATE pending_alliances SET clan1 = ? WHERE clan1 = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("UPDATE pending_alliances SET clan2 = ? WHERE clan2 = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                // Update player_clan_history table
                try (PreparedStatement ps = con.prepareStatement("UPDATE player_clan_history SET current_clan = ? WHERE current_clan = ?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }
} 
