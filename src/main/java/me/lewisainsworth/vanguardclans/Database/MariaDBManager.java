package me.lewisainsworth.vanguardclans.Database;
import me.lewisainsworth.vanguardclans.VanguardClan;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import me.lewisainsworth.vanguardclans.Utils.MSG;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.lewisainsworth.vanguardclans.Utils.FileHandler;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.*;

public class MariaDBManager extends AbstractStorageProvider {
    private final VanguardClan plugin;
    private final FileConfiguration config;
    private HikariDataSource dataSource;
    private final Map<String, String> playerClanCache = new HashMap<>();
    private final Set<String> clanNamesCache = new HashSet<>();
    private final Map<String, String> clanColoredNameCache = new HashMap<>();
    private long lastCacheUpdate = 0;

    public MariaDBManager(FileConfiguration config) {
        super(config);
        this.config = config;
        this.plugin = VanguardClan.getInstance();

        String host = config.getString("storage.mariadb.host");
        int port = config.getInt("storage.mariadb.port");
        String database = config.getString("storage.mariadb.database");
        String user = config.getString("storage.mariadb.username");
        String password = config.getString("storage.mariadb.password");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true");
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);

        // IMPORTANTE: usar driver sombreado si usás shading
        hikariConfig.setDriverClassName("me.lewisainsworth.shaded.mariadb.jdbc.Driver");

        hikariConfig.setMaximumPoolSize(75);
        hikariConfig.setMinimumIdle(20);          
        hikariConfig.setConnectionTimeout(10000);  
        hikariConfig.setIdleTimeout(300000);       
        hikariConfig.setMaxLifetime(1800000); 

        dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource not initialized");
        }
        return dataSource.getConnection();
    }

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
                    role VARCHAR(32) DEFAULT 'member',
                    PRIMARY KEY (clan, username)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clan_roles (
                    clan VARCHAR(255),
                    role VARCHAR(32),
                    permissions TEXT,
                    PRIMARY KEY (clan, role)
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
                CREATE TABLE IF NOT EXISTS reports (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    clan VARCHAR(255),
                    reason TEXT
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS economy_players (
                    player VARCHAR(36) PRIMARY KEY,
                    balance DOUBLE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_clan_history (
                    uuid VARCHAR(36) NOT NULL,
                    name VARCHAR(16),
                    current_clan VARCHAR(255),
                    history TEXT,
                    PRIMARY KEY (uuid)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clan_invites (
                    clan VARCHAR(255),
                    username VARCHAR(36),
                    PRIMARY KEY (clan, username),
                    invite_time BIGINT NOT NULL DEFAULT 0
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pending_alliances (
                    clan1 VARCHAR(255),
                    clan2 VARCHAR(255),
                    PRIMARY KEY (clan1, clan2)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS friendlyfire_allies (
                    clan VARCHAR(255) PRIMARY KEY,
                    enabled BOOLEAN
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clan_homes (
                    clan VARCHAR(255) PRIMARY KEY,
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    username VARCHAR(16) PRIMARY KEY,
                    kills INT DEFAULT 0,
                    deaths INT DEFAULT 0
                )
            """);
        }

        try (Connection con = getConnection();
            Statement stmt = con.createStatement()) {

            ResultSet rs = con.getMetaData().getColumns(null, null, "clans", "name_colored");
            if (!rs.next()) {
                stmt.executeUpdate("ALTER TABLE clans ADD COLUMN name_colored TEXT");
                Bukkit.getLogger().info("Columna 'name_colored' agregada a la tabla 'clans'.");
            }

            ResultSet pointsColumn = con.getMetaData().getColumns(null, null, "clans", "points");
            if (!pointsColumn.next()) {
                stmt.executeUpdate("ALTER TABLE clans ADD COLUMN points INT DEFAULT 0");
                Bukkit.getLogger().info("Columna 'points' agregada a la tabla 'clans'.");
            }

            ResultSet upgradesColumn = con.getMetaData().getColumns(null, null, "clans", "slot_upgrades");
            if (!upgradesColumn.next()) {
                stmt.executeUpdate("ALTER TABLE clans ADD COLUMN slot_upgrades INT DEFAULT 0");
                Bukkit.getLogger().info("Columna 'slot_upgrades' agregada a la tabla 'clans'.");
            }

            ResultSet roleColumn = con.getMetaData().getColumns(null, null, "clan_users", "role");
            if (!roleColumn.next()) {
                stmt.executeUpdate("ALTER TABLE clan_users ADD COLUMN role VARCHAR(32) DEFAULT 'member'");
                Bukkit.getLogger().info("Columna 'role' agregada a la tabla 'clan_users'.");
            }

        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error al verificar o agregar la columna 'name_colored': " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void deleteClanHome(String clan) {
        try (Connection con = getConnection();
            PreparedStatement ps = con.prepareStatement("DELETE FROM clan_homes WHERE clan = ?")) {
            ps.setString(1, clan);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void forceJoin(String playerName, String clanName) {
        try (Connection con = getConnection()) {
            // Verificar si el clan existe
            try (PreparedStatement checkClan = con.prepareStatement("SELECT name FROM clans WHERE name = ?")) {
                checkClan.setString(1, clanName);
                try (ResultSet rs = checkClan.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("[VanguardClans] Clan '" + clanName + "' no existe.");
                        return;
                    }
                }
            }

            // Eliminar usuario de cualquier clan anterior
            try (PreparedStatement removeOld = con.prepareStatement("DELETE FROM clan_users WHERE username = ?")) {
                removeOld.setString(1, playerName);
                removeOld.executeUpdate();
            }

            // Insertar al nuevo clan
            try (PreparedStatement insert = con.prepareStatement("INSERT INTO clan_users (clan, username) VALUES (?, ?)")) {
                insert.setString(1, clanName);
                insert.setString(2, playerName);
                insert.executeUpdate();
            }

            System.out.println("[VanguardClans] Jugador " + playerName + " forzado a unirse al clan " + clanName + ".");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void forceLeave(String playerName) {
        try (Connection con = getConnection();
            PreparedStatement stmt = con.prepareStatement("DELETE FROM clan_users WHERE username = ?")) {

            stmt.setString(1, playerName);
            int affected = stmt.executeUpdate();

            if (affected > 0) {
                System.out.println("[VanguardClans] Jugador " + playerName + " fue forzado a salir del clan.");
            } else {
                System.out.println("[VanguardClans] El jugador " + playerName + " no estaba en ningún clan.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteClan(String clanName) {
        try (Connection con = getConnection()) {
            // Eliminar aliados
            try (PreparedStatement stmt = con.prepareStatement("DELETE FROM alliances WHERE clan1 = ? OR clan2 = ?")) {
                stmt.setString(1, clanName);
                stmt.setString(2, clanName);
                stmt.executeUpdate();
            }

            // Eliminar usuarios del clan
            try (PreparedStatement stmt = con.prepareStatement("DELETE FROM clan_users WHERE clan = ?")) {
                stmt.setString(1, clanName);
                stmt.executeUpdate();
            }

            // Eliminar solicitudes de alianza pendientes
            try (PreparedStatement stmt = con.prepareStatement("DELETE FROM pending_alliances WHERE clan1 = ? OR clan2 = ?")) {
                stmt.setString(1, clanName);
                stmt.setString(2, clanName);
                stmt.executeUpdate();
            }

            // Eliminar home
            try (PreparedStatement stmt = con.prepareStatement("DELETE FROM clan_homes WHERE clan = ?")) {
                stmt.setString(1, clanName);
                stmt.executeUpdate();
            }

            // Eliminar configuraciones de FF
            try (PreparedStatement stmt = con.prepareStatement("DELETE FROM friendlyfire WHERE clan = ?")) {
                stmt.setString(1, clanName);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = con.prepareStatement("DELETE FROM friendlyfire_allies WHERE clan = ?")) {
                stmt.setString(1, clanName);
                stmt.executeUpdate();
            }

            // Eliminar invitaciones
            try (PreparedStatement stmt = con.prepareStatement("DELETE FROM clan_invites WHERE clan = ?")) {
                stmt.setString(1, clanName);
                stmt.executeUpdate();
            }

            // Eliminar reportes
            try (PreparedStatement stmt = con.prepareStatement("DELETE FROM reports WHERE clan = ?")) {
                stmt.setString(1, clanName);
                stmt.executeUpdate();
            }

            // Eliminar roles del clan
            try (PreparedStatement stmt = con.prepareStatement("DELETE FROM clan_roles WHERE clan = ?")) {
                stmt.setString(1, clanName);
                stmt.executeUpdate();
            }

            // Finalmente eliminar el clan
            try (PreparedStatement stmt = con.prepareStatement("DELETE FROM clans WHERE name = ?")) {
                stmt.setString(1, clanName);
                int affected = stmt.executeUpdate();

                if (affected > 0) {
                    System.out.println("[VanguardClans] Clan '" + clanName + "' eliminado correctamente.");
                } else {
                    System.out.println("[VanguardClans] El clan '" + clanName + "' no existe.");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // This method is kept for backward compatibility
    @Override
    public javax.sql.DataSource getDataSource() {
        return this.dataSource;
    }


    public void syncFromYaml(FileConfiguration data) throws SQLException {
        if (!data.contains("Clans")) return;

        try (
            Connection con = getConnection();
            PreparedStatement insertClan = con.prepareStatement(
                "REPLACE INTO clans (name, founder, leader, money, privacy) VALUES (?, ?, ?, ?, ?)");
            PreparedStatement insertUser = con.prepareStatement(
                "REPLACE INTO clan_users (clan, username) VALUES (?, ?)")
        ) {
            Set<String> clans = data.getConfigurationSection("Clans").getKeys(false);
            for (String clan : clans) {
                String path = "Clans." + clan;
                insertClan.setString(1, clan);
                insertClan.setString(2, data.getString(path + ".Founder"));
                insertClan.setString(3, data.getString(path + ".Leader"));
                insertClan.setDouble(4, data.getDouble(path + ".Money"));
                insertClan.setString(5, data.getString(path + ".Privacy"));
                insertClan.executeUpdate();

                List<String> users = data.getStringList(path + ".Users");
                for (String user : users) {
                    insertUser.setString(1, clan);
                    insertUser.setString(2, user);
                    insertUser.executeUpdate();
                }
            }
        }
    }

    public void setClanHome(String clan, Location location) {
        try (Connection con = getConnection();
            PreparedStatement ps = con.prepareStatement("""
                REPLACE INTO clan_homes (clan, world, x, y, z) VALUES (?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, clan);
            ps.setString(2, location.getWorld().getName());
            ps.setDouble(3, location.getX());
            ps.setDouble(4, location.getY());
            ps.setDouble(5, location.getZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Location getClanHome(String clan) {
        try (Connection con = getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT * FROM clan_homes WHERE clan = ?")) {
            ps.setString(1, clan);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String world = rs.getString("world");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    World bukkitWorld = Bukkit.getWorld(world);
                    if (bukkitWorld == null) return null;
                    return new Location(bukkitWorld, x, y, z);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    public void clearYamlClans(FileConfiguration data, FileHandler fh) {
        data.set("Clans", null);
        fh.saveData();
    }

    public void reloadCache() {
        playerClanCache.clear();
        clanNamesCache.clear();

        try (Connection con = getConnection();
             PreparedStatement stmt1 = con.prepareStatement("SELECT username, clan FROM clan_users");
             ResultSet rs1 = stmt1.executeQuery()) {

            while (rs1.next()) {
                playerClanCache.put(rs1.getString("username").toLowerCase(), rs1.getString("clan"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (Connection con = getConnection();
            PreparedStatement stmt2 = con.prepareStatement("SELECT name, name_colored FROM clans");
            ResultSet rs2 = stmt2.executeQuery()) {

            while (rs2.next()) {
                String name = rs2.getString("name");
                String colored = rs2.getString("name_colored");

                clanNamesCache.add(name);
                clanColoredNameCache.put(name, colored != null ? colored : name); // fallback
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        lastCacheUpdate = System.currentTimeMillis();
    }


    public double getKillDeathRatio(String playerName) {
        try (Connection con = plugin.getStorageProvider().getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT kills, deaths FROM player_stats WHERE username = ?")) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int kills = rs.getInt("kills");
                int deaths = rs.getInt("deaths");
                if (deaths == 0) return kills;
                return (double) kills / deaths;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public void fixClanColorsAsync(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final int[] fixed = {0}; // Usamos un array de 1 elemento para contador mutable
            try (Connection con = getConnection()) {
                con.setAutoCommit(false);
                
                try {
                    // Reparar inconsistencias en nombres de clanes
                    try (PreparedStatement select = con.prepareStatement("SELECT name_colored, name FROM clans WHERE name_colored != name");
                         PreparedStatement update = con.prepareStatement("UPDATE clans SET name_colored=? WHERE name=?")) {

                        ResultSet rs = select.executeQuery();
                        while (rs.next()) {
                            String raw = rs.getString("name");

                            update.setString(1, raw);
                            update.setString(2, raw);
                            update.addBatch();
                            fixed[0]++;
                        }
                        update.executeBatch();
                    }
                    
                    // Reparar referencias inconsistentes en otras tablas
                    String[] tables = {"clan_users", "friendlyfire", "clan_invites", "reports", "alliances", "pending_alliances", "player_clan_history"};
                    
                    for (String table : tables) {
                        try (PreparedStatement ps = con.prepareStatement("UPDATE " + table + " SET clan = (SELECT name FROM clans WHERE name_colored = " + table + ".clan LIMIT 1) WHERE clan IN (SELECT name_colored FROM clans WHERE name_colored != name)")) {
                            ps.executeUpdate();
                        } catch (SQLException e) {
                            // Ignorar errores si la tabla no existe o no tiene la columna clan
                        }
                    }
                    
                    con.commit();
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getStorageProvider().reloadCache();
                        sender.sendMessage(MSG.color("&aSe han corregido &f" + fixed[0] + " &aclanes, eliminando colores del nombre y reparando la detección de clan."));
                    });

                } catch (SQLException e) {
                    con.rollback();
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }

            } catch (SQLException e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(MSG.color("&cOcurrió un error al intentar reparar los colores de los clanes. Revisa la consola."))
                );
            }
        });
    }



    /**
     * Obtiene el nombre coloreado de un clan, o devuelve el nombre plano si no existe.
     */
    public String getColoredName(String clanName) {
        if (clanName == null) return null;
        try (Connection con = getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT name_colored FROM clans WHERE name=?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String colored = rs.getString("name_colored");
                return colored != null ? colored : clanName;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return clanName;
    }

    /**
     * Obtiene el nombre plano (raw) de un clan dado un nombre coloreado.
     */
    public String getRawNameFromColored(String coloredName) {
        if (coloredName == null) return null;
        try (Connection con = getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT name FROM clans WHERE name_colored=?")) {
            ps.setString(1, coloredName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Verifica si existe un clan por nombre plano.
     */
    public boolean clanExists(String clanName) {
        try (Connection con = getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT 1 FROM clans WHERE name=?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    

    public String getCachedPlayerClan(String playerName) {
        ensureCacheFresh();
        return playerClanCache.getOrDefault(playerName.toLowerCase(), null);
    }

    public Map<String, String> getPlayerClanCache() {
        ensureCacheFresh();
        return playerClanCache;
    }

    @Override
    public Set<String> getCachedClanNames() {
        ensureCacheFresh();
        return new HashSet<>(clanNamesCache);
    }

    @Override
    protected void ensureCacheFresh() {
        if (System.currentTimeMillis() - lastCacheUpdate > 5 * 60 * 1000) {
            reloadCache();
        }
    }

    @Override
    protected void repairClanLeadershipImpl(CommandSender sender) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Find clans without leaders
                String sql = "SELECT c.name FROM clans c LEFT JOIN clan_users u ON c.name = u.clan AND u.role = 'leader' WHERE u.username IS NULL";
                try (PreparedStatement ps = con.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    
                    while (rs.next()) {
                        String clanName = rs.getString("name");
                        // Find the first member to make leader
                        String updateSql = "UPDATE clan_users SET role = 'leader' WHERE clan = ? AND username = (SELECT username FROM clan_users WHERE clan = ? LIMIT 1)";
                        try (PreparedStatement updatePs = con.prepareStatement(updateSql)) {
                            updatePs.setString(1, clanName);
                            updatePs.setString(2, clanName);
                            updatePs.executeUpdate();
                        }
                    }
                }
                con.commit();
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(MSG.color("&aReparación de liderazgo de clanes completada."))
                );
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    @Override
    protected void fixClanColorsImpl(CommandSender sender) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Get all clans with colored names
                String sql = "SELECT name, name_colored FROM clans WHERE name_colored IS NOT NULL";
                try (PreparedStatement ps = con.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    
                    while (rs.next()) {
                        String clanName = rs.getString("name");
                        String coloredName = rs.getString("name_colored");
                        
                        // Strip colors and update if different
                        String strippedName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', coloredName));
                        if (!clanName.equals(strippedName)) {
                            String updateSql = "UPDATE clans SET name_colored = ? WHERE name = ?";
                            try (PreparedStatement updatePs = con.prepareStatement(updateSql)) {
                                updatePs.setString(1, strippedName);
                                updatePs.setString(2, clanName);
                                updatePs.executeUpdate();
                            }
                        }
                    }
                }
                con.commit();
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(MSG.color("&aReparación de colores de clanes completada."))
                );
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    @Override
    protected void addPlayerClanHistoryImpl(String playerName, String clanName, String action, long timestamp) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "INSERT INTO player_clan_history (uuid, clan_name, action, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, playerName);
                ps.setString(2, clanName);
                ps.setString(3, action);
                ps.setLong(4, timestamp);
                ps.executeUpdate();
            }
        }
    }

    @Override
    protected List<Map<String, Object>> getPlayerClanHistoryImpl(String playerName) throws Exception {
        // Implementation for MariaDB
        List<Map<String, Object>> history = new ArrayList<>();
        try (Connection con = getConnection()) {
            String sql = "SELECT clan_name, action, timestamp FROM player_clan_history WHERE uuid = ? ORDER BY timestamp DESC";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, playerName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("clan_name", rs.getString("clan_name"));
                        entry.put("action", rs.getString("action"));
                        entry.put("timestamp", rs.getLong("timestamp"));
                        history.add(entry);
                    }
                }
            }
        }
        return history;
    }

    @Override
    protected void addClanReportImpl(String clanName, String reporter, String reason, long timestamp) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "INSERT INTO clan_reports (clan_name, reporter, reason, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ps.setString(2, reporter);
                ps.setString(3, reason);
                ps.setLong(4, timestamp);
                ps.executeUpdate();
            }
        }
    }

    @Override
    protected List<Map<String, Object>> getClanReportsImpl(String clanName) throws Exception {
        // Implementation for MariaDB
        List<Map<String, Object>> reports = new ArrayList<>();
        try (Connection con = getConnection()) {
            String sql = "SELECT reporter, reason, timestamp FROM clan_reports WHERE clan_name = ? ORDER BY timestamp DESC";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> report = new HashMap<>();
                        report.put("reporter", rs.getString("reporter"));
                        report.put("reason", rs.getString("reason"));
                        report.put("timestamp", rs.getLong("timestamp"));
                        reports.add(report);
                    }
                }
            }
        }
        return reports;
    }

    @Override
    protected void removePendingAllianceImpl(String clan1, String clan2) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "DELETE FROM pending_alliances WHERE (clan1 = ? AND clan2 = ?) OR (clan1 = ? AND clan2 = ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clan1);
                ps.setString(2, clan2);
                ps.setString(3, clan2);
                ps.setString(4, clan1);
                ps.executeUpdate();
            }
        }
    }

    @Override
    protected void addPendingAllianceImpl(String clan1, String clan2) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "INSERT INTO pending_alliances (clan1, clan2) VALUES (?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clan1);
                ps.setString(2, clan2);
                ps.executeUpdate();
            }
        }
    }

    @Override
    protected List<String> getPendingAlliancesImpl(String clanName) throws Exception {
        // Implementation for MariaDB
        List<String> pendingAlliances = new ArrayList<>();
        try (Connection con = getConnection()) {
            String sql = "SELECT clan2 FROM pending_alliances WHERE clan1 = ? UNION SELECT clan1 FROM pending_alliances WHERE clan2 = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ps.setString(2, clanName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        pendingAlliances.add(rs.getString(1));
                    }
                }
            }
        }
        return pendingAlliances;
    }

    @Override
    protected boolean isPlayerInvitedToClanImpl(String playerName, String clanName) throws Exception {
        // Implementation for MariaDB
        long cutoff = getInviteCutoff();
        try (Connection con = getConnection()) {
            String sql = "SELECT 1 FROM clan_invites WHERE clan = ? AND username = ? AND invite_time >= ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ps.setString(2, playerName);
                ps.setLong(3, cutoff);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    @Override
    protected void removeClanInviteImpl(String clanName, String playerName) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "DELETE FROM clan_invites WHERE clan = ? AND username = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ps.setString(2, playerName);
                ps.executeUpdate();
            }
        }
    }

    @Override
    protected void addClanInviteImpl(String clanName, String playerName) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "INSERT INTO clan_invites (clan, username, invite_time) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE invite_time = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                ps.setString(1, clanName);
                ps.setString(2, playerName);
                ps.setLong(3, now);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
        }
    }

    @Override
    protected List<String> getClanInvitesImpl(String clanName) throws Exception {
        // Implementation for MariaDB
        List<String> invites = new ArrayList<>();
        long cutoff = getInviteCutoff();
        try (Connection con = getConnection()) {
            String sql = "SELECT username FROM clan_invites WHERE clan = ? AND invite_time >= ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ps.setLong(2, cutoff);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        invites.add(rs.getString("username"));
                    }
                }
            }
        }
        return invites;
    }

    @Override
    protected void cleanupExpiredInvitesImpl(long cutoff) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM clan_invites WHERE invite_time < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        }
    }

    @Override
    protected void setFriendlyFireImpl(String clanName, boolean enabled) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "INSERT INTO friendlyfire (clan, enabled) VALUES (?, ?) ON DUPLICATE KEY UPDATE enabled = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ps.setBoolean(2, enabled);
                ps.setBoolean(3, enabled);
                ps.executeUpdate();
            }
        }
    }

    @Override
    protected boolean getFriendlyFireImpl(String clanName) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "SELECT enabled FROM friendlyfire WHERE clan = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("enabled");
                    }
                }
            }
        }
        return false; // Default to false if not found
    }

    @Override
    protected void removeAllianceImpl(String clan1, String clan2) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "DELETE FROM alliances WHERE (clan1 = ? AND clan2 = ?) OR (clan1 = ? AND clan2 = ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clan1);
                ps.setString(2, clan2);
                ps.setString(3, clan2);
                ps.setString(4, clan1);
                ps.executeUpdate();
            }
        }
    }

    @Override
    protected void createAllianceImpl(String clan1, String clan2, boolean friendlyFire) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "INSERT INTO alliances (clan1, clan2, friendly_fire) VALUES (?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clan1);
                ps.setString(2, clan2);
                ps.setBoolean(3, friendlyFire);
                ps.executeUpdate();
            }
        }
    }

    @Override
    protected List<String> getClanAlliancesImpl(String clanName) throws Exception {
        // Implementation for MariaDB
        List<String> alliances = new ArrayList<>();
        try (Connection con = getConnection()) {
            String sql = "SELECT clan2 FROM alliances WHERE clan1 = ? UNION SELECT clan1 FROM alliances WHERE clan2 = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ps.setString(2, clanName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        alliances.add(rs.getString(1));
                    }
                }
            }
        }
        return alliances;
    }

    @Override
    protected void setClanHomeImpl(String clanName, Location location) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "INSERT INTO clan_homes (clan, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
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
    }

    @Override
    protected Location getClanHomeImpl(String clanName) throws Exception {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "SELECT world, x, y, z, yaw, pitch FROM clan_homes WHERE clan = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String worldName = rs.getString("world");
                        World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            return new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected List<String> getClanMembersImpl(String clanName) throws Exception {
        // Implementation for MariaDB
        List<String> members = new ArrayList<>();
        try (Connection con = getConnection()) {
            String sql = "SELECT username FROM clan_users WHERE clan = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        members.add(rs.getString("username"));
                    }
                }
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
        long cutoff = getInviteCutoff();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT clan FROM clan_invites WHERE username = ? AND invite_time >= ?")) {
            ps.setString(1, playerName);
            ps.setLong(2, cutoff);
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
    protected int getPlayerKillsImpl(String playerName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT kills FROM player_stats WHERE username = ?")) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("kills") : 0;
        }
    }

    @Override
    protected int getPlayerDeathsImpl(String playerName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT deaths FROM player_stats WHERE username = ?")) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("deaths") : 0;
        }
    }

    @Override
    protected String getPlayerRoleImpl(String clanName, String playerName) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT role FROM clan_users WHERE clan = ? AND username = ?")) {
            ps.setString(1, clanName);
            ps.setString(2, playerName);
            ResultSet rs = ps.executeQuery();
            String role = rs.next() ? rs.getString("role") : null;
            return role == null || role.trim().isEmpty() ? "member" : role;
        }
    }

    @Override
    protected void setPlayerRoleImpl(String clanName, String playerName, String role) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clan_users SET role = ? WHERE clan = ? AND username = ?")) {
            ps.setString(1, role);
            ps.setString(2, clanName);
            ps.setString(3, playerName);
            if (ps.executeUpdate() == 0) {
                try (PreparedStatement insert = con.prepareStatement(
                    "INSERT INTO clan_users (clan, username, role) VALUES (?, ?, ?)")) {
                    insert.setString(1, clanName);
                    insert.setString(2, playerName);
                    insert.setString(3, role);
                    insert.executeUpdate();
                }
            }
        }
    }

    @Override
    protected Map<String, Set<String>> getClanRolesImpl(String clanName) throws Exception {
        Map<String, Set<String>> roles = new HashMap<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT role, permissions FROM clan_roles WHERE clan = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String role = rs.getString("role");
                String raw = rs.getString("permissions");
                Set<String> perms = new HashSet<>();
                if (raw != null && !raw.trim().isEmpty()) {
                    for (String entry : raw.split(",")) {
                        String trimmed = entry.trim();
                        if (!trimmed.isEmpty()) {
                            perms.add(trimmed);
                        }
                    }
                }
                roles.put(role.toLowerCase(Locale.ROOT), perms);
            }
        }
        return roles;
    }

    @Override
    protected void setClanRoleImpl(String clanName, String role, Set<String> permissions) throws Exception {
        String joined = String.join(",", permissions);
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "INSERT INTO clan_roles (clan, role, permissions) VALUES (?, ?, ?) " +
                 "ON DUPLICATE KEY UPDATE permissions = VALUES(permissions)")) {
            ps.setString(1, clanName);
            ps.setString(2, role.toLowerCase(Locale.ROOT));
            ps.setString(3, joined);
            ps.executeUpdate();
        }
    }

    @Override
    protected void deleteClanRoleImpl(String clanName, String role) throws Exception {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM clan_roles WHERE clan = ? AND role = ?")) {
            ps.setString(1, clanName);
            ps.setString(2, role.toLowerCase(Locale.ROOT));
            ps.executeUpdate();
        }
    }

    @Override
    public void importFromYaml(Map<String, Object> data) {
        // Implementation for MariaDB - import data from YAML format
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Clear existing data
                con.createStatement().executeUpdate("DELETE FROM clans");
                con.createStatement().executeUpdate("DELETE FROM clan_users");
                con.createStatement().executeUpdate("DELETE FROM alliances");
                con.createStatement().executeUpdate("DELETE FROM clan_homes");
                con.createStatement().executeUpdate("DELETE FROM clan_invites");
                con.createStatement().executeUpdate("DELETE FROM pending_alliances");
                con.createStatement().executeUpdate("DELETE FROM clan_reports");
                con.createStatement().executeUpdate("DELETE FROM player_clan_history");
                con.createStatement().executeUpdate("DELETE FROM friendlyfire");

                // Import clans
                if (data.containsKey("clans")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> clans = (Map<String, Object>) data.get("clans");
                    for (Map.Entry<String, Object> entry : clans.entrySet()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> clanData = (Map<String, Object>) entry.getValue();
                        
                        String sql = "INSERT INTO clans (name, name_colored, founder, leader, money, privacy, points, slot_upgrades) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                        try (PreparedStatement ps = con.prepareStatement(sql)) {
                            ps.setString(1, entry.getKey());
                            ps.setString(2, (String) clanData.get("name_colored"));
                            ps.setString(3, (String) clanData.get("founder"));
                            ps.setString(4, (String) clanData.get("leader"));
                            ps.setDouble(5, ((Number) clanData.get("money")).doubleValue());
                            ps.setString(6, (String) clanData.get("privacy"));
                            ps.setInt(7, clanData.containsKey("points") ? ((Number) clanData.get("points")).intValue() : 0);
                            ps.setInt(8, clanData.containsKey("slot_upgrades") ? ((Number) clanData.get("slot_upgrades")).intValue() : 0);
                            ps.executeUpdate();
                        }
                    }
                }

                // Import clan members
                if (data.containsKey("clan_members")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> clanMembers = (Map<String, Object>) data.get("clan_members");
                    for (Map.Entry<String, Object> entry : clanMembers.entrySet()) {
                        String sql = "INSERT INTO clan_users (clan, username) VALUES (?, ?)";
                        try (PreparedStatement ps = con.prepareStatement(sql)) {
                            ps.setString(1, (String) entry.getValue());
                            ps.setString(2, entry.getKey());
                            ps.executeUpdate();
                        }
                    }
                }

                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, Object> exportToYaml() {
        // Implementation for MariaDB - export data to YAML format
        Map<String, Object> data = new HashMap<>();
        
        try (Connection con = getConnection()) {
            // Export clans
            Map<String, Object> clans = new HashMap<>();
            String clanSql = "SELECT name, name_colored, founder, leader, money, privacy, points, slot_upgrades FROM clans";
            try (PreparedStatement ps = con.prepareStatement(clanSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> clanData = new HashMap<>();
                    clanData.put("name_colored", rs.getString("name_colored"));
                    clanData.put("founder", rs.getString("founder"));
                    clanData.put("leader", rs.getString("leader"));
                    clanData.put("money", rs.getDouble("money"));
                    clanData.put("privacy", rs.getString("privacy"));
                    clanData.put("points", rs.getInt("points"));
                    clanData.put("slot_upgrades", rs.getInt("slot_upgrades"));
                    clans.put(rs.getString("name"), clanData);
                }
            }
            data.put("clans", clans);

            // Export clan members
            Map<String, Object> clanMembers = new HashMap<>();
            String memberSql = "SELECT username, clan FROM clan_users";
            try (PreparedStatement ps = con.prepareStatement(memberSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    clanMembers.put(rs.getString("username"), rs.getString("clan"));
                }
            }
            data.put("clan_members", clanMembers);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return data;
    }

    @Override
    public void migrateFrom(StorageProvider source) throws Exception {
        // Implementation for MariaDB - migrate data from another storage provider
        Map<String, Object> data = source.exportToYaml();
        importFromYaml(data);
    }

    @Override
    public void updateClanName(String oldName, String newName, String coloredName) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Update clan name
                String sql = "UPDATE clans SET name = ?, name_colored = ? WHERE name = ?";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, newName);
                    ps.setString(2, coloredName);
                    ps.setString(3, oldName);
                    ps.executeUpdate();
                }

                // Update clan_users table
                String userSql = "UPDATE clan_users SET clan = ? WHERE clan = ?";
                try (PreparedStatement ps = con.prepareStatement(userSql)) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                // Update alliances table
                String allianceSql = "UPDATE alliances SET clan1 = ? WHERE clan1 = ?";
                try (PreparedStatement ps = con.prepareStatement(allianceSql)) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                String allianceSql2 = "UPDATE alliances SET clan2 = ? WHERE clan2 = ?";
                try (PreparedStatement ps = con.prepareStatement(allianceSql2)) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                // Update other tables that reference clan names
                String[] tables = {"clan_homes", "clan_invites", "pending_alliances", "clan_reports", "friendlyfire", "clan_roles"};
                for (String table : tables) {
                    String updateSql = "UPDATE " + table + " SET clan = ? WHERE clan = ?";
                    try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                        ps.setString(1, newName);
                        ps.setString(2, oldName);
                        ps.executeUpdate();
                    }
                }

                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateClanLeader(String clanName, String newLeader) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "UPDATE clans SET leader = ? WHERE name = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, newLeader);
                ps.setString(2, clanName);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removePlayerFromClan(String playerName, String clanName) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "DELETE FROM clan_users WHERE username = ? AND clan = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, playerName);
                ps.setString(2, clanName);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addPlayerToClan(String playerName, String clanName) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "INSERT INTO clan_users (username, clan, role) VALUES (?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, playerName);
                ps.setString(2, clanName);
                ps.setString(3, "member");
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void createClan(String clanName, String leader, String founder, String coloredName, double money, String tag) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // Create the clan
                String clanSql = "INSERT INTO clans (name, leader, founder, name_colored, money, privacy, points, slot_upgrades) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(clanSql)) {
                    ps.setString(1, clanName);
                    ps.setString(2, leader);
                    ps.setString(3, founder);
                    ps.setString(4, coloredName);
                    ps.setDouble(5, money);
                    ps.setString(6, tag);
                    ps.setInt(7, 0);
                    ps.setInt(8, 0);
                    ps.executeUpdate();
                }

                // Add leader to clan_users
                String userSql = "INSERT INTO clan_users (clan, username, role) VALUES (?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(userSql)) {
                    ps.setString(1, clanName);
                    ps.setString(2, leader);
                    ps.setString(3, "leader");
                    ps.executeUpdate();
                }

                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getClanColoredName(String clanName) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "SELECT name_colored FROM clans WHERE name = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getString("name_colored");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void setClanPrivacy(String clanName, String privacy) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "UPDATE clans SET privacy = ? WHERE name = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, privacy);
                ps.setString(2, clanName);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getClanPrivacy(String clanName) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "SELECT privacy FROM clans WHERE name = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getString("privacy");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void setClanMoney(String clanName, double money) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "UPDATE clans SET money = ? WHERE name = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setDouble(1, money);
                ps.setString(2, clanName);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public double getClanMoney(String clanName) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "SELECT money FROM clans WHERE name = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getDouble("money");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    @Override
    public int getClanPoints(String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT points FROM clans WHERE name = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("points") : 0;
        } catch (Exception e) {
            e.printStackTrace();
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addClanPoints(String clanName, int delta) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE clans SET points = GREATEST(0, points + ?) WHERE name = ?")) {
            ps.setInt(1, delta);
            ps.setString(2, clanName);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getClanSlotUpgrades(String clanName) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT slot_upgrades FROM clans WHERE name = ?")) {
            ps.setString(1, clanName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("slot_upgrades") : 0;
        } catch (Exception e) {
            e.printStackTrace();
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getClanFounder(String clanName) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "SELECT founder FROM clans WHERE name = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getString("founder");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getClanLeader(String clanName) {
        // Implementation for MariaDB
        try (Connection con = getConnection()) {
            String sql = "SELECT leader FROM clans WHERE name = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, clanName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getString("leader");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void initialize() {
        // Implementation for MariaDB - tables are created in constructor
        // This method is called after the storage provider is created
        try {
            setupTables();
            reloadCache();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getColoredClanName(String clan) {
        ensureCacheFresh();
        return clanColoredNameCache.getOrDefault(clan, clan);
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
