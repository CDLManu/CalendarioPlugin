package it.cdl.calendario;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
// import it.tuodominio.calendario.EventManager; // <-- Questo import era mancante

/**
 * Classe principale del plugin CalendarioPlugin.
 * Gestisce il ciclo di vita del plugin, l'inizializzazione dei componenti,
 * e la registrazione di comandi e listener.
 */
public final class CalendarioPlugin extends JavaPlugin {

    private TimeManager timeManager;
    private BossBarManager bossBarManager;
    private SeasonalEffectsManager seasonalEffectsManager;
    private EventManager eventManager; // <-- Ora viene riconosciuto
    private CalendarTask mainTaskInstance;
    private boolean debugMode;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.debugMode = getConfig().getBoolean("debug-mode", false);

        startupPluginSystems();
        registerCommandsAndListeners();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CalendarioExpansion(this).register();
            getLogger().info("PlaceholderAPI trovato! I placeholder sono stati attivati.");
        } else {
            getLogger().warning("PlaceholderAPI non trovato. I placeholder non saranno disponibili.");
        }

        getLogger().info("CalendarioPlugin abilitato con successo!");
    }

    @Override
    public void onDisable() {
        if (timeManager != null) {
            timeManager.saveData();
        }
        shutdownPluginSystems();
        getLogger().info("CalendarioPlugin disabilitato.");
    }

    public void shutdownPluginSystems() {
        if (mainTaskInstance != null && !mainTaskInstance.isCancelled()) {
            mainTaskInstance.cancel();
        }
        if (seasonalEffectsManager != null) {
            seasonalEffectsManager.stopAllEffects();
        }
        if (bossBarManager != null) {
            bossBarManager.removeAllPlayers();
        }
        for (World world : this.getServer().getWorlds()) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        }
        getLogger().info("Sistemi del plugin arrestati. Game rule 'doDaylightCycle' ripristinata.");
    }

    public void startupPluginSystems() {
        this.timeManager = new TimeManager(this);
        this.bossBarManager = new BossBarManager(this);
        this.seasonalEffectsManager = new SeasonalEffectsManager(this);
        this.eventManager = new EventManager(this); // Inizializzazione corretta

        for (World world : this.getServer().getWorlds()) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        }
        getLogger().info("Game rule 'doDaylightCycle' impostata su 'false'. Il tempo è gestito dal plugin.");

        this.mainTaskInstance = new CalendarTask(this);
        this.mainTaskInstance.runTaskTimer(this, 0L, 20L);

        for (Player player : this.getServer().getOnlinePlayers()) {
            bossBarManager.addPlayer(player);
        }
        this.seasonalEffectsManager.handleSeasonChange(this.timeManager.getEnumStagioneCorrente());
    }

    private void registerCommandsAndListeners() {
        this.getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        this.getServer().getPluginManager().registerEvents(new CropGrowthListener(this), this);
        this.getServer().getPluginManager().registerEvents(new SleepListener(this), this);
        this.getServer().getPluginManager().registerEvents(this.seasonalEffectsManager, this);

        PluginCommand calendarCommand = this.getCommand("calendario");
        if (calendarCommand != null) {
            CalendarCommand executorAndCompleter = new CalendarCommand(this);
            calendarCommand.setExecutor(executorAndCompleter);
            calendarCommand.setTabCompleter(executorAndCompleter);
        } else {
            getLogger().severe("Comando 'calendario' non trovato! Controlla il plugin.yml");
        }
    }

    // --- Metodi "Getter" ---
    public CalendarTask getMainTaskInstance() { return mainTaskInstance; }
    public TimeManager getTimeManager() { return timeManager; }
    public BossBarManager getBossBarManager() { return bossBarManager; }
    public SeasonalEffectsManager getSeasonalEffectsManager() { return seasonalEffectsManager; }
    public EventManager getEventManager() { return eventManager; } // <-- Ora viene riconosciuto

    public boolean isDebugMode() {
        return this.debugMode;
    }
}