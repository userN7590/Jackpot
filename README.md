🎰 JackpotPlugin
A milestone-based jackpot contribution system built for Minecraft servers using the Vault economy API. Visual tracking via the BossBar API.

📦 Features
🔹 Milestone Contributions
Players contribute currency to a global jackpot. Once the total reaches defined thresholds, milestone rewards are triggered via server commands.

🌟 Tiered Milestones with Custom UI
Each milestone has a configurable title, reward, and boss bar color. Progress is displayed in real time using the BossBar API with support for color coded titles (ChatColor) and percentage-based filling.

⏳ The End Phase
After all milestones are completed, a final countdown ("The End") begins. A separate boss bar with its own color and title depletes over time. When it reaches zero, final configurable commands are executed.

💰 Vault Economy Integration
Fully integrated with Vault to support most economy plugins. Contributions are validated, deducted, and stored securely using Vault’s API.

📁 YAML-Driven Configuration
Milestones and final phase settings are defined in config.yml, including amount thresholds, bar visuals, and triggered commands—enabling simple reconfiguration without code edits.

🧠 Technical Highlights

✅ Java Concepts Used

Concept	Application
Object-Oriented Programming	Encapsulates milestone data via an internal Milestone class
Bukkit BossBar API	Visual milestone + countdown progression in real time
BukkitRunnable	Used for scheduled jackpot increments and end-phase countdown timers
File I/O	Reads/writes persistent data to jackpotData.yml using YamlConfiguration
Dependency Injection	Retrieves Vault economy provider using RegisteredServiceProvider
Event Handling	Listens to player join events to add them to active BossBar
PlaceholderAPI	Registers custom %jackpot_*% placeholders for real-time scoreboard data

🧪 Example: Final Phase BossBar Logic
String title = ChatColor.translateAlternateColorCodes('&', getConfig().getString("the_end.bar_title"));
BarColor color = BarColor.valueOf(getConfig().getString("the_end.bar_color").toUpperCase());
bossBar.setTitle(title);
bossBar.setColor(color);
double progress = (double) endPhaseTimeLeft / getConfig().getInt("the_end.duration_seconds");
bossBar.setProgress(Math.max(0, Math.min(1.0, progress)));
