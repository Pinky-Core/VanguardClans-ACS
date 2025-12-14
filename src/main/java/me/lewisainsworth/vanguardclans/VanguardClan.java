package me.lewisainsworth.vanguardclans;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import me.lewisainsworth.vanguardclans.CMDs.CCMD;
import me.lewisainsworth.vanguardclans.CMDs.ACMD;
import me.lewisainsworth.vanguardclans.CMDs.PECMD;
import me.lewisainsworth.vanguardclans.CMDs.LangCMD;
import me.lewisainsworth.vanguardclans.Events.Events;
import me.lewisainsworth.vanguardclans.Utils.*;
import me.lewisainsworth.vanguardclans.Database.MariaDBManager;
import me.lewisainsworth.vanguardclans.Database.StorageProvider;
import me.lewisainsworth.vanguardclans.Database.StorageFactory;
import me.lewisainsworth.vanguardclans.listeners.PlayerStatsListener;
import me.lewisainsworth.vanguardclans.Utils.NameTagManager;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.util.Objects;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;



public class VanguardClan extends JavaPlugin {
   public String version = getDescription().getVersion();
   public static String prefix;
   public static Econo econ;

   private Updater updater;
   private Metrics metrics;
   private FileHandler fh;
   private StorageProvider storageProvider;
   public LangManager langManager;
   private LangCMD langCMD;
   private CCMD ccCmd;
   private NameTagManager nameTagManager;

   private static VanguardClan instance;

   public Set<UUID> teleportingPlayers = new HashSet<>();
   public Map<UUID, Long> homeCooldowns = new HashMap<>();
   private final Set<UUID> clanChatToggled = new HashSet<>();
   public int clanHomeCooldown;
   public int clanHomeDelay;
   

   @Override
   public void onEnable() {
      instance = this;
      saveDefaultConfig();
      this.clanHomeCooldown = getConfig().getInt("clan_home.cooldown", 30);
      this.clanHomeDelay = getConfig().getInt("clan_home.teleport_delay", 5);
      prefix = getConfig().getString("prefix", "&7 [&a&lᴠᴀɴɢᴜᴀʀᴅ&6&lᴄʟᴀɴꜱ&7]&n");
      fh = new FileHandler(this);
      updater = new Updater(this, 126207);
      metrics = new Metrics(this, 20912);
      econ = new Econo(this);
      ClanUtils.init(this);
      copyLangFiles();
      langManager = new LangManager(this);
      LangCMD langCMD = new LangCMD(this);
      getServer().getPluginManager().registerEvents(new PlayerStatsListener(this), this);
      setLangCMD(langCMD);

      this.ccCmd = new CCMD(this, langManager);
      getCommand("clan").setExecutor(ccCmd);

      if (getConfig().getBoolean("economy.enabled", true)) {
         if (!econ.setupEconomy()) {
            getLogger().severe("Can´t load the economy system.");
            fh.getConfig().set("economy.enabled", false);
            fh.saveConfig();
            getLogger().severe("Economy system disabled.");
            return;
         }
      }

      fh.saveDefaults();

      try {
         String storageType = getConfig().getString("storage.type", "yaml");
         storageProvider = StorageFactory.createStorageProvider(storageType, getConfig());
         getLogger().info("Storage provider initialized: " + storageType);
      } catch (Exception e) {
         getLogger().severe("Failed to initialize storage provider: " + e.getMessage());
         getLogger().severe("Falling back to YAML storage...");
         try {
            storageProvider = StorageFactory.createStorageProvider("yaml", getConfig());
            getLogger().info("YAML storage provider initialized as fallback.");
         } catch (Exception fallbackError) {
            getLogger().severe("Failed to initialize fallback storage: " + fallbackError.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
         }
      }

      nameTagManager = new NameTagManager(this);

      setupMetrics();
      registerCommands();
      registerEvents();
      searchUpdates();

      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         new PAPI(this).registerPlaceholders();
         getLogger().info("Placeholders de VanguardClans registrados correctamente.");
      }

      // Enhanced startup message
      Bukkit.getConsoleSender().sendMessage(MSG.color("&2&l============================================================"));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&a&l    VanguardClans &2Enabled Successfully!"));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&7"));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lVersion: &f" + getDescription().getVersion()));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lAuthor: &f" + String.join(", ", getDescription().getAuthors())));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lStorage Type: &f" + getConfig().getString("storage.type", "yaml").toUpperCase()));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lEconomy: &f" + (getConfig().getBoolean("economy.enabled", true) ? "&aEnabled" : "&cDisabled")));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lPlaceholderAPI: &f" + (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null ? "&aHooked" : "&cNot Found")));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lMax Clans: &f" + (getConfig().getInt("max-clans", -1) <= 0 ? "Unlimited" : getConfig().getInt("max-clans"))));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&7"));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&2&l============================================================"));
   }


   @Override
   public void onDisable() {
      if (storageProvider != null) {
         storageProvider.close();
         getLogger().info("Storage provider closed successfully.");
      }
      if (nameTagManager != null) {
         nameTagManager.shutdown();
      }
      
      // Enhanced shutdown message
      Bukkit.getConsoleSender().sendMessage(MSG.color("&c&l============================================================"));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&c&l    VanguardClans Disabled!"));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&7"));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lVersion: &f" + getDescription().getVersion()));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&7&lAll clan data has been saved."));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&7&lThank you for using VanguardClans!"));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&7"));
      Bukkit.getConsoleSender().sendMessage(MSG.color("&c&l============================================================"));
   }

   public static VanguardClan getInstance() {
      return instance;
   }

   private void copyLangFiles() {
      File langFolder = new File(getDataFolder(), "lang");
      if (!langFolder.exists()) {
         langFolder.mkdirs();
      }

      String[] languages = {"es.yml", "en.yml"};  // pon aquí todos los idiomas que tengas

      for (String langFile : languages) {
         File file = new File(langFolder, langFile);
         if (!file.exists()) {
               saveResource("lang/" + langFile, false);
         }
      }
   }


   private void setupMetrics() {
      int max = getConfig().getInt("max-clans", -1);
      String maxClans = (max <= 0) ? "Unlimited" : String.valueOf(max);

      metrics.addCustomChart(new Metrics.SimplePie("economy_enabled",
              () -> String.valueOf(getConfig().getBoolean("economy.enabled", true))));
      metrics.addCustomChart(new Metrics.SimplePie("economy_system",
              () -> getConfig().getString("economy.system", "Unknown")));
      metrics.addCustomChart(new Metrics.SimplePie("max_clans",
              () -> maxClans));
   }

   private void registerCommands() {
      Objects.requireNonNull(getCommand("clanadmin")).setExecutor(new ACMD(this));
      Objects.requireNonNull(getCommand("clan")).setExecutor(new CCMD(this, langManager));
      Objects.requireNonNull(getCommand("scstats")).setExecutor(new PECMD(this));
   }

   private void registerEvents() {
      getServer().getPluginManager().registerEvents(new Events(this, ccCmd), this);
   }

   public void searchUpdates() {
      String downloadUrl = "https://www.spigotmc.org/resources/vanguardclans-advanced-clans-system.126207";
      TextComponent link = new TextComponent(MSG.color("&6&lClick here to download the update!"));
      link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, downloadUrl));

      boolean updateAvailable = false;
      String latestVersion = "unknown";

      try {
         updater = new Updater(this, 126207);
         updateAvailable = updater.isUpdateAvailable();
         latestVersion = updater.getLatestVersion();
      } catch (Exception e) {
         Bukkit.getConsoleSender().sendMessage(MSG.color("&cError checking for updates: " + e.getMessage()));
      }

      if (updateAvailable) {
          Bukkit.getConsoleSender().sendMessage(MSG.color("&2&l============================================================"));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&6&l         ＮＥＷ  ＶＥＲＳＩＯＮ  ＡＶＡＩＬＡＢＬＥ!"));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&7"));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lPlugin: &fVanguardClans"));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lCurrent Version: &f" + version));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lLatest Version: &f" + latestVersion));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&e&lDownload: &b" + downloadUrl));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&7"));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&a&lChangelog &7(see plugin page for details)"));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&7- Bug fixes and improvements"));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&7- New features may be available!"));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&7"));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&c&lPlease update to enjoy the latest features and fixes!"));
          Bukkit.getConsoleSender().sendMessage(MSG.color("&2&l============================================================"));

         for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("VanguardClans.admin")) {
                  player.sendMessage(MSG.color(prefix + "&e A new plugin update is available!"));
               player.spigot().sendMessage(link);
            }
         }
      }
   }

   public boolean isClanBanned(String clanName) {
      return getStorageProvider().isClanBanned(clanName);
   }

   public boolean isClanChatToggled(Player player) {
      return clanChatToggled.contains(player.getUniqueId());
   }

   public void toggleClanChat(Player player) {
      if (isClanChatToggled(player)) {
         clanChatToggled.remove(player.getUniqueId());
      } else {
         clanChatToggled.add(player.getUniqueId());
      }
   }


   public String getPlayerClan(String playerName) {
      if (playerName == null || playerName.isEmpty()) {
         return null;
      }
      
      return this.getStorageProvider().getPlayerClan(playerName);
   }



   public boolean isWorldBlocked(World world) {
      return getConfig().getStringList("blocked-worlds").contains(world.getName());
   }

   public static Econo getEcon() {
      return econ;
   }

   public FileHandler getFH() {
      return fh;
   }

   public StorageProvider getStorageProvider() {
      return storageProvider;
   }

   public NameTagManager getNameTagManager() {
      return nameTagManager;
   }

   // Legacy method for backward compatibility
   public MariaDBManager getMariaDBManager() {
      if (storageProvider instanceof MariaDBManager) {
         return (MariaDBManager) storageProvider;
      }
      throw new UnsupportedOperationException("Current storage provider is not MariaDB");
   }

   public LangManager getLangManager() {
      return langManager;
   }

   public LangCMD getLangCMD() {
      return langCMD;
   }

   public void setLangCMD(LangCMD langCMD) {
      this.langCMD = langCMD;
   }
}
