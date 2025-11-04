package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.VanguardClan;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class Updater {
    private final VanguardClan plugin;
    private final int resourceId;

    public Updater(VanguardClan plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public String getLatestVersion() throws IOException {
        URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (InputStream inputStream = connection.getInputStream();
             Scanner scanner = new Scanner(inputStream)) {
            if (scanner.hasNext()) {
                return scanner.next();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to retrieve the latest version: " + e.getMessage());
            throw e;
        }
        return null;
    }

    public boolean isUpdateAvailable() {
        try {
            String currentVersion = plugin.getDescription().getVersion();
            String latestVersion = getLatestVersion();
            
            if (latestVersion == null) {
                return false;
            }
            
            // Compare versions properly (e.g., 1.4.7 vs 1.4.6)
            return compareVersions(latestVersion, currentVersion) > 0;
        } catch (IOException e) {
            plugin.getLogger().severe("Error checking for updates: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Compare two version strings
     * @return positive if v1 > v2, negative if v1 < v2, 0 if equal
     */
    private int compareVersions(String v1, String v2) {
        // Remove any non-numeric suffixes (e.g., 1.4.7-SNAPSHOT -> 1.4.7)
        v1 = v1.split("-")[0];
        v2 = v2.split("-")[0];
        
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        
        return 0;
    }
    
    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
