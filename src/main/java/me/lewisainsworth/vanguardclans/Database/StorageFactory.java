package me.lewisainsworth.vanguardclans.Database;

import me.lewisainsworth.vanguardclans.VanguardClan;
import org.bukkit.configuration.file.FileConfiguration;

public class StorageFactory {
    
    public static StorageProvider createStorageProvider(String type, FileConfiguration config) throws Exception {
        StorageProvider provider;
        
        switch (type.toLowerCase()) {
            case "mariadb":
                provider = new MariaDBManager(config);
                break;
            case "mysql":
                provider = new MySQLStorageProvider(config);
                break;
            case "h2":
                provider = new H2StorageProvider(config);
                break;
            case "sqlite":
                provider = new SQLiteStorageProvider(config);
                break;
            case "yaml":
                provider = new YamlStorageProvider(config);
                break;
            default:
                throw new IllegalArgumentException("Unsupported storage type: " + type);
        }
        
        provider.initialize();
        return provider;
    }
    
    public static String getDefaultStorageType() {
        return "yaml"; // Default to YAML for simplicity
    }
    
    public static String[] getSupportedStorageTypes() {
        return new String[]{"mariadb", "mysql", "h2", "sqlite", "yaml"};
    }
} 
