package com.fil.jackpot;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public class JackpotPlaceholderExpansion extends PlaceholderExpansion {

    private final Main plugin;

    public JackpotPlaceholderExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "jackpot";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        switch (identifier.toLowerCase()) {
            case "total":
                return plugin.formatCurrency(plugin.getJackpotTotal());

            case "milestone":
                double nextMilestone = plugin.getNextMilestone();
                return (nextMilestone == -1) ? "All milestones reached!" : plugin.formatCurrency(nextMilestone);

            case "reward":
                return plugin.getLastMilestoneReward();

            case "current_title":
                return plugin.getCurrentMilestoneTitle();

            case "next_title":
                return plugin.getNextMilestoneTitle();

            default:
                return null;
        }
    }
}