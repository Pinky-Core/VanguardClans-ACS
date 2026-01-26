package me.lewisainsworth.vanguardclans.Utils;

public class ClanTopEntry {
    private final String clanName;
    private final String coloredName;
    private final int members;
    private final int points;
    private final double money;
    private final int kills;
    private final int deaths;
    private final double totalKda;
    private final double averageKda;

    public ClanTopEntry(String clanName, String coloredName, int members, int points, double money,
                        int kills, int deaths, double totalKda, double averageKda) {
        this.clanName = clanName;
        this.coloredName = coloredName;
        this.members = members;
        this.points = points;
        this.money = money;
        this.kills = kills;
        this.deaths = deaths;
        this.totalKda = totalKda;
        this.averageKda = averageKda;
    }

    public String getClanName() {
        return clanName;
    }

    public String getColoredName() {
        return coloredName;
    }

    public int getMembers() {
        return members;
    }

    public int getPoints() {
        return points;
    }

    public double getMoney() {
        return money;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public double getTotalKda() {
        return totalKda;
    }

    public double getAverageKda() {
        return averageKda;
    }

    public double getSortValue(TopMetric metric) {
        if (metric == null) {
            return 0.0;
        }
        switch (metric) {
            case KDA:
                return totalKda;
            case POINTS:
                return points;
            case MONEY:
                return money;
            case MEMBERS:
                return members;
            default:
                return 0.0;
        }
    }
}
