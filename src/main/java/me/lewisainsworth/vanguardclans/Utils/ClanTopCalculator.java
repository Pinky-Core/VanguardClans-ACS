package me.lewisainsworth.vanguardclans.Utils;

import me.lewisainsworth.vanguardclans.Database.StorageProvider;
import me.lewisainsworth.vanguardclans.VanguardClan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ClanTopCalculator {

    private final VanguardClan plugin;

    public ClanTopCalculator(VanguardClan plugin) {
        this.plugin = plugin;
    }

    public List<ClanTopEntry> getTopEntries(TopMetric metric) {
        StorageProvider storage = plugin.getStorageProvider();
        Set<String> clans = storage.getAllClans();
        List<ClanTopEntry> entries = new ArrayList<>();

        for (String clan : clans) {
            String coloredName = storage.getClanColoredName(clan);
            int members = storage.getClanMemberCount(clan);
            int points = storage.getClanPoints(clan);
            double money = storage.getClanMoney(clan);

            int kills = 0;
            int deaths = 0;
            double kdaSum = 0.0;
            int memberCount = 0;

            List<String> clanMembers = storage.getClanMembers(clan);
            for (String member : clanMembers) {
                int playerKills = storage.getPlayerKills(member);
                int playerDeaths = storage.getPlayerDeaths(member);
                kills += playerKills;
                deaths += playerDeaths;
                double playerKda = playerDeaths == 0 ? playerKills : (double) playerKills / playerDeaths;
                kdaSum += playerKda;
                memberCount++;
            }

            double totalKda = deaths == 0 ? kills : (double) kills / deaths;
            double averageKda = memberCount == 0 ? 0.0 : kdaSum / memberCount;

            entries.add(new ClanTopEntry(
                clan,
                coloredName,
                members,
                points,
                money,
                kills,
                deaths,
                totalKda,
                averageKda
            ));
        }

        entries.sort(Comparator.comparingDouble((ClanTopEntry entry) -> entry.getSortValue(metric)).reversed());
        return entries;
    }
}
