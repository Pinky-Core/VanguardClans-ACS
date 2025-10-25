package me.lewisainsworth.vanguardclans.Utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import me.lewisainsworth.vanguardclans.VanguardClan;

import java.io.File;
import java.io.IOException;

public class FileHandler {
    private VanguardClan plugin;
    private FileConfiguration dataFileConfig = null;
    private final File dataFile;
    private FileConfiguration configFileConfig = null;
    private final File configFile;

    public FileHandler(VanguardClan plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        registerDataFile();
        registerConfigFile();
    }

    private void registerDataFile() {
        if (!dataFile.exists()) {
            if (plugin.getResource("data.yml") != null) {
                plugin.saveResource("data.yml", false);
            } else {
                try {
                    dataFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("No se pudo crear data.yml: " + e.getMessage());
                }
            }
        }
        dataFileConfig = YamlConfiguration.loadConfiguration(dataFile);
    }


    private void registerConfigFile() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        configFileConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveDefaults() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
    }

    public void saveData() {
        try {
            if (dataFileConfig != null) {
                dataFileConfig.save(dataFile);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data.yml: " + e.getMessage());
        }
    }

    public void loadData() {
        dataFileConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void reloadData() {
        loadData();
    }

    public FileConfiguration getData() {
        if (dataFileConfig == null) {
            loadData();
        }
        return dataFileConfig;
    }

    public void saveConfig() {
        try {
            if (configFileConfig != null) {
                configFileConfig.save(configFile);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save config.yml: " + e.getMessage());
        }
    }

    public void loadConfig() {
        configFileConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfig() {
        loadConfig();
    }

    public FileConfiguration getConfig() {
        if (configFileConfig == null) {
            loadConfig();
        }
        return configFileConfig;
    }
}
