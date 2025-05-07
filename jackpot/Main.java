package com.fil.jackpot;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class Main extends JavaPlugin implements Listener {

    private Economy economy;
    private double jackpotTotal = 0;
    private List<Milestone> milestones = new ArrayList<>();
    private double autoIncrement = 1.0;
    private int nextMilestoneIndex = 0;
    private File jackpotDataFile;
    private FileConfiguration jackpotData;
    private static final DecimalFormat numberFormat = new DecimalFormat("#,###.##");
    private String lastMilestoneReward = "No rewards yet!";
    private BossBar bossBar;

    // The End phase
    private boolean inEndPhase = false;
    private int endPhaseTimeLeft;
    private int endPhaseDuration;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMilestones();
        loadJackpotData();

        if (!setupEconomy()) {
            getLogger().severe("Vault dependency not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        startAutoIncrementTask();
        updateBossBar();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("JackpotPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        saveJackpotData();
        getLogger().info("JackpotPlugin has been disabled!");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (bossBar != null) {
            bossBar.addPlayer(event.getPlayer());
        }
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    private void loadMilestones() {
        milestones.clear();
        ConfigurationSection milestonesSection = getConfig().getConfigurationSection("milestones");

        if (milestonesSection == null) {
            getLogger().warning("No 'milestones' section found in config.yml.");
            return;
        }

        for (String key : milestonesSection.getKeys(false)) {
            double amount = milestonesSection.getDouble(key + ".amount");
            String command = milestonesSection.getString(key + ".command");
            String title = milestonesSection.getString(key + ".title", "No Title");
            String rewardMessage = milestonesSection.getString(key + ".reward_message", "No reward specified.");
            String color = milestonesSection.getString(key + ".bar_color", "BLUE");

            if (command == null) {
                getLogger().warning("Milestone " + key + " has no command set.");
                continue;
            }

            milestones.add(new Milestone(amount, command, title, rewardMessage, color));
        }

        milestones.sort(Comparator.comparingDouble(Milestone::getAmount));
        getLogger().info("Loaded " + milestones.size() + " milestones.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("jackpot")) return false;

        if (args.length == 0) {
            sender.sendMessage("Usage: /jackpot <amount> OR /jackpot reload OR /jackpot view");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("jackpot.reload")) {
                sender.sendMessage("You do not have permission to reload the plugin.");
                return true;
            }
            reloadConfig();
            loadMilestones();
            saveJackpotData();
            sender.sendMessage("Jackpot configuration reloaded!");
            updateBossBar();
            return true;
        }

        if (args[0].equalsIgnoreCase("view")) {
            double nextMilestone = getNextMilestone();
            sender.sendMessage("Current Jackpot: " + formatCurrency(jackpotTotal));
            sender.sendMessage("Next Milestone: " + (nextMilestone > 0 ? formatCurrency(nextMilestone) : "All milestones reached!"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can contribute to the jackpot.");
            return true;
        }

        Player player = (Player) sender;
        double amount;

        try {
            amount = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("Invalid amount.");
            return true;
        }

        if (amount <= 0) {
            player.sendMessage("Amount must be greater than 0.");
            return true;
        }

        double nextMilestone = getNextMilestone();
        if (nextMilestone == -1) {
            player.sendMessage("All milestones have been reached!");
            return true;
        }

        double needed = nextMilestone - jackpotTotal;
        if (amount > needed) {
            amount = needed;
        }

        if (!economy.has(player, amount)) {
            player.sendMessage("You don't have enough money.");
            return true;
        }

        economy.withdrawPlayer(player, amount);
        jackpotTotal += amount;
        player.sendMessage("You contributed " + formatCurrency(amount) + " to the jackpot. Total: " + formatCurrency(jackpotTotal));

        checkMilestones();
        saveJackpotData();
        updateBossBar();
        return true;
    }

    public double getNextMilestone() {
        if (nextMilestoneIndex >= milestones.size()) return -1;
        return milestones.get(nextMilestoneIndex).getAmount();
    }

    private void checkMilestones() {
        if (nextMilestoneIndex >= milestones.size()) {
            if (!inEndPhase) {
                startEndPhase();
            }
            return;
        }

        Milestone milestone = milestones.get(nextMilestoneIndex);
        if (jackpotTotal >= milestone.getAmount()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), milestone.getCommand());
            lastMilestoneReward = milestone.getRewardMessage();

            jackpotTotal = 0;
            autoIncrement = 1.0;
            nextMilestoneIndex++;

            saveJackpotData();
            updateBossBar();
        }
    }

    private void startAutoIncrementTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (inEndPhase) return;

                double nextMilestone = getNextMilestone();
                if (nextMilestone == -1) return;

                jackpotTotal += autoIncrement;
                checkMilestones();
                saveJackpotData();
                updateBossBar();

                autoIncrement *= 1.02;
            }
        }.runTaskTimer(this, 1200, 1200);
    }

    private void startEndPhase() {
        inEndPhase = true;
        endPhaseDuration = getConfig().getInt("the_end.duration_seconds", 86400);
        endPhaseTimeLeft = endPhaseDuration;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (endPhaseTimeLeft <= 0) {
                    for (String cmd : getConfig().getStringList("the_end.commands")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }
                    this.cancel();
                    return;
                }

                endPhaseTimeLeft--;
                updateBossBar();
            }
        }.runTaskTimer(this, 20, 20);
    }

    private void updateBossBar() {
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
            bossBar.setVisible(true);
            Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);
        }

        // Handle End Phase first
        if (inEndPhase) {
            int totalDuration = getConfig().getInt("the_end.duration_seconds", 86400);
            double progress = (double) endPhaseTimeLeft / totalDuration;

            String title = ChatColor.translateAlternateColorCodes('&', getConfig().getString("the_end.bar_title", "&cThe End is Near!"));
            BarColor color = BarColor.valueOf(getConfig().getString("the_end.bar_color", "RED").toUpperCase());

            bossBar.setTitle(title);  // Title from config
            bossBar.setColor(color);  // Color from config
            bossBar.setProgress(Math.max(0, Math.min(1.0, progress)));  // Safe progress bounds

            getLogger().info("[DEBUG] End phase active - Title: " + title + ", Progress: " + progress + ", Time left: " + endPhaseTimeLeft + "/" + totalDuration);
            return;
        }

        // Handle Milestones
        if (nextMilestoneIndex >= milestones.size()) {
            bossBar.setTitle("All Ages Reached!");
            bossBar.setColor(BarColor.RED);
            bossBar.setProgress(1.0);
            return;
        }

        // Current milestone logic
        Milestone current = milestones.get(nextMilestoneIndex);
        double milestoneAmount = current.getAmount();
        double milestoneProgress = jackpotTotal / milestoneAmount;

        bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', current.getTitle()));
        bossBar.setColor(BarColor.valueOf(current.getColor().toUpperCase()));
        bossBar.setProgress(Math.min(1.0, milestoneProgress));

        getLogger().info("[DEBUG] Milestone active - Title: " + current.getTitle() + ", Progress: " + milestoneProgress);
    }

    private void loadJackpotData() {
        jackpotDataFile = new File(getDataFolder(), "jackpotData.yml");
        if (!jackpotDataFile.exists()) {
            saveJackpotData();
        }

        jackpotData = YamlConfiguration.loadConfiguration(jackpotDataFile);
        jackpotTotal = jackpotData.getDouble("jackpotTotal", 0);
        nextMilestoneIndex = jackpotData.getInt("nextMilestoneIndex", 0);
        autoIncrement = jackpotData.getDouble("autoIncrement", 1.0);

        // Load end phase duration first from config
        endPhaseDuration = getConfig().getInt("the_end.duration_seconds", 86400);
        endPhaseTimeLeft = jackpotData.getInt("endPhaseTimeLeft", endPhaseDuration);
        inEndPhase = jackpotData.getBoolean("inEndPhase", false);

        getLogger().info("[DEBUG] Jackpot data loaded. End phase: " + inEndPhase + ", time left: " + endPhaseTimeLeft);
    }

    private void saveJackpotData() {
        if (jackpotData == null) return;

        jackpotData.set("jackpotTotal", jackpotTotal);
        jackpotData.set("nextMilestoneIndex", nextMilestoneIndex);
        jackpotData.set("autoIncrement", autoIncrement);
        jackpotData.set("inEndPhase", inEndPhase);
        jackpotData.set("endPhaseTimeLeft", endPhaseTimeLeft);

        try {
            jackpotData.save(jackpotDataFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save jackpot data!");
        }
    }

    public double getJackpotTotal() {
        return jackpotTotal;
    }

    public String getLastMilestoneReward() {
        return lastMilestoneReward;
    }

    public String getCurrentMilestoneTitle() {
        if (nextMilestoneIndex == 0) return "Iron Age";
        return milestones.get(nextMilestoneIndex - 1).getTitle();
    }

    public String getNextMilestoneTitle() {
        if (nextMilestoneIndex >= milestones.size()) return "All milestones reached!";
        return milestones.get(nextMilestoneIndex).getTitle();
    }

    public static String formatCurrency(double amount) {
        if (amount % 1 == 0) {
            return String.format("$%,d", (long) amount);
        } else {
            return "$" + numberFormat.format(amount);
        }
    }

    private static class Milestone {
        private final double amount;
        private final String command;
        private final String title;
        private final String rewardMessage;
        private final String color;

        public Milestone(double amount, String command, String title, String rewardMessage, String color) {
            this.amount = amount;
            this.command = command;
            this.title = title;
            this.rewardMessage = rewardMessage;
            this.color = color;
        }

        public double getAmount() {
            return amount;
        }

        public String getCommand() {
            return command;
        }

        public String getTitle() {
            return title;
        }

        public String getRewardMessage() {
            return rewardMessage;
        }

        public String getColor() {
            return color;
        }
    }
}
