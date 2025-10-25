package me.lewisainsworth.vanguardclans.Utils;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

import me.lewisainsworth.vanguardclans.VanguardClan;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.bukkit.Bukkit.getServer;
import static me.lewisainsworth.vanguardclans.VanguardClan.prefix;

public class Econo {

    private final VanguardClan plugin;
    private String system;

    public static Economy vault;
    private final Map<UUID, Double> internalBalances = new HashMap<>();
    private File balanceFile;
    private FileConfiguration balanceCfg;

    public Econo(VanguardClan plugin) {
        this.plugin = plugin;
    }

    public String getSystem() {
        if (system == null) {
            FileConfiguration cfg = plugin.getConfig();
            system = cfg.getString("economy.system", "Vault").toLowerCase();
        }
        return system;
    }

    public String reload() {
        if (system == null) {
            FileConfiguration cfg = plugin.getConfig();
            system = cfg.getString("economy.system", "Vault").toLowerCase();
        }
        return system;
    }

    public boolean setupEconomy() {
        switch (getSystem()) {
            case "vault":
                RegisteredServiceProvider<Economy> provider = getServer()
                        .getServicesManager()
                        .getRegistration(Economy.class);
                vault = (provider != null) ? provider.getProvider() : null;
                if (vault != null) return true;

                Bukkit.getConsoleSender().sendMessage(MSG.color(prefix + "&cVault provider is null, using Internal Econ."));
                system = "internal";
                loadInternal();
                return true;

            case "internal":
                loadInternal();
                return true;

            default:
                Bukkit.getConsoleSender().sendMessage(MSG.color(prefix + "&cEconomy system is null!: " + system));
                return false;
        }
    }

    public double getBalance(OfflinePlayer player) {
        return switch (getSystem()) {
            case "vault" -> vault.getBalance(player);
            case "internal" -> internalBalances.getOrDefault(player.getUniqueId(), 0.0);
            default -> 0;
        };
    }

    public void deposit(OfflinePlayer player, double amount) {
        if (amount < 0) return;
        switch (getSystem()) {
            case "vault":
                vault.depositPlayer(player, amount).transactionSuccess();
                return;
            case "internal":
                internalBalances.merge(player.getUniqueId(), amount, Double::sum);
                saveInternal();
                return;
            default:
        }
    }

    public void withdraw(OfflinePlayer player, double amount) {
        if (amount < 0) return;
        switch (getSystem()) {
            case "vault":
                vault.withdrawPlayer(player, amount).transactionSuccess();
                return;
            case "internal":
                UUID id = player.getUniqueId();
                double current = internalBalances.getOrDefault(id, 0.0);
                if (current < amount) return;
                internalBalances.put(id, current - amount);
                saveInternal();
                return;
            default:
        }
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (amount < 0) return false;
        return switch (getSystem()) {
            case "vault" -> vault.has(player, amount);
            case "internal" -> internalBalances.getOrDefault(player.getUniqueId(), 0.0) >= amount;
            default -> false;
        };
    }

    private void loadInternal() {
        balanceFile = new File(plugin.getDataFolder(), "balances.yml");
        if (!balanceFile.exists()) {
            balanceFile.getParentFile().mkdirs();
            try {
                balanceFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        balanceCfg = YamlConfiguration.loadConfiguration(balanceFile);

        for (String key : balanceCfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                double money = balanceCfg.getDouble(key + ".Money", 0.0);
                internalBalances.put(id, money);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveInternal() {
        if (balanceCfg == null) return;

        for (String key : balanceCfg.getKeys(false)) {
            balanceCfg.set(key, null);
        }

        for (Map.Entry<UUID, Double> entry : internalBalances.entrySet()) {
            UUID uuid = entry.getKey();
            double bal = entry.getValue();
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = "Unknown";

            balanceCfg.set(uuid + ".Name", name);
            balanceCfg.set(uuid + ".Money", bal);
        }

        try {
            balanceCfg.save(balanceFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
