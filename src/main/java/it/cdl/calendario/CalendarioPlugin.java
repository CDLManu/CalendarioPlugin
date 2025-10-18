package it.cdl.calendario;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
/**
 * Classe principale del plugin CalendarioPlugin.
 * Gestisce il ciclo di vita del plugin (abilitazione, disabilitazione, ricaricamento),
 * inizializza tutti i manager e i listener, e funge da punto di accesso centrale
 * per tutti i componenti del sistema.
 */
public final class CalendarioPlugin extends JavaPlugin {

    // --- Manager del Plugin ---
    private TimeManager timeManager;
    private BossBarManager bossBarManager;
    private SeasonalEffectsManager seasonalEffectsManager;
    private EventManager eventManager;
    private LanguageManager languageManager;
    private CalendarTask mainTaskInstance;

    private boolean debugMode;
    /**
     * Metodo chiamato all'avvio del server o al caricamento del plugin.
     * Si occupa di inizializzare la configurazione, i file di lingua e tutti
     * i sistemi principali del plugin.
     */
    @Override
    public void onEnable() {
        //Creazione e salvataggio dei file di default se non esistono.
        setupDefaultFiles();

        //Caricamento della configurazione e inizializzazione dei manager.
        //Salva le nuove sezioni di config se non esistono
        this.saveDefaultConfig();
        this.reloadConfig();
        this.debugMode = getConfig().getBoolean("debug-mode", false);
        this.languageManager = new LanguageManager(this);

        //Avvio dei sistemi principali del plugin.
        startupPluginSystems();
        registerCommandsAndListeners();

        //Integrazione con API esterne (PlaceholderAPI).
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CalendarioExpansion(this).register();
            getLogger().info(languageManager.getString("logs.papi-found"));
        } else {
            getLogger().warning(languageManager.getString("logs.papi-not-found"));
        }

        getLogger().info("CalendarioPlugin è stato abilitato con successo!");
    }

    /**
     * Metodo chiamato allo spegnimento del server o alla disabilitazione del plugin.
     * Salva i dati correnti e ferma tutti i task per garantire una chiusura pulita.
     */
    @Override
    public void onDisable() {
        if (timeManager != null) {
            timeManager.saveData();
            // Salva la data in data.yml
        }
        shutdownPluginSystems();
        getLogger().info("CalendarioPlugin è stato disabilitato.");
    }

    /**
     * Gestisce il ricaricamento della configurazione del plugin tramite comando.
     * Questo metodo salva prima i dati di gioco correnti (come la data) su data.yml,
     * poi ricarica le impostazioni da config.yml e infine riavvia i sistemi del plugin.
     * Questo garantisce che né i dati di gioco né le modifiche manuali alla configurazione
     * vengano persi durante il processo.
     */
    public void reload() {
        getLogger().info(languageManager.getString("logs.plugin-reloading"));


        //Salva i dati correnti (la data) nel file data.yml PRIMA di fare qualsiasi altra cosa.
        if (timeManager != null) {
            timeManager.saveData();
        }
        // Ferma tutti i sistemi attivi per prepararsi al riavvio.
        shutdownPluginSystems();

        // Ricarica SOLO i file di configurazione (config.yml, events.yml, etc.).
        reloadConfig();

        // Re-inizializza i componenti che dipendono dalla configurazione appena caricata.
        this.debugMode = getConfig().getBoolean("debug-mode", false);
        this.languageManager = new LanguageManager(this);

        // Fa ripartire tutti i sistemi. TimeManager caricherà la data salvata da data.yml o, se non è stato riavviato, manterrà quella che ha già in memoria.
        startupPluginSystems();

        //Forza l'aggiornamento dei sistemi per applicare subito le nuove impostazioni lette dal config.yml (es. Nuova velocità del tempo).
        if (mainTaskInstance != null) {
            mainTaskInstance.forceUpdate();
        }

        getLogger().info(languageManager.getString("logs.plugin-reloaded"));
    }

    /**
     * Inizializza e avvia tutti i sistemi principali del plugin,
     * come i manager e il task del calendario.
     * Imposta la gamerule per prendere il controllo del ciclo giorno/notte.
     */
    public void startupPluginSystems() {
        this.timeManager = new TimeManager(this);
        // Caricherà i dati da data.yml
        this.bossBarManager = new BossBarManager(this);
        this.seasonalEffectsManager = new SeasonalEffectsManager(this);
        this.eventManager = new EventManager(this);

        // Disabilita il ciclo giorno/notte di default per prenderne il controllo.
        for (World world : this.getServer().getWorlds()) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        }
        getLogger().info(languageManager.getString("logs.gamerule-set"));

        // Avvia il task principale che gestisce il tempo.
        this.mainTaskInstance = new CalendarTask(this);
        this.mainTaskInstance.runTaskTimer(this, 0L, 20L);
        // Eseguito ogni secondo

        // Aggiunge i giocatori online alla BossBar e applica effetti stagionali.
        for (Player player : this.getServer().getOnlinePlayers()) {
            bossBarManager.addPlayer(player);
        }
        this.seasonalEffectsManager.handleSeasonChange(this.timeManager.getEnumStagioneCorrente());
    }

    /**
     * Ferma tutti i sistemi attivi del plugin e ripristina le impostazioni
     * di default del gioco (es. Ciclo giorno/notte).
     */
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
        // Ripristina il ciclo giorno/notte di default di Minecraft.
        for (World world : this.getServer().getWorlds()) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        }
        getLogger().info(languageManager.getString("logs.gamerule-restored"));
    }

    /**
     * Registra tutti i comandi e i listener necessari per il funzionamento del plugin.
     */
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
            getLogger().severe(languageManager.getString("logs.command-not-found"));
        }
    }

    /**
     * Si assicura che i file di configurazione e di lingua di default esistano
     * nella cartella del plugin, creandoli se necessario.
     */
    private void setupDefaultFiles() {
        saveDefaultConfig();
        saveResource("events.yml", false);
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) {
            if (!langFolder.mkdirs()) {
                getLogger().severe("Impossibile creare la cartella 'lang'!");
            }
        }
        saveResource("lang/en_US.yml", false);
        saveResource("lang/it_IT.yml", false);
    }

    // --- Metodi Getter per l'accesso ai componenti del plugin ---

    public TimeManager getTimeManager() { return timeManager;
    }
    public BossBarManager getBossBarManager() { return bossBarManager; }
    public SeasonalEffectsManager getSeasonalEffectsManager() { return seasonalEffectsManager;
    }
    public EventManager getEventManager() { return eventManager; }
    public LanguageManager getLanguageManager() { return languageManager;
    }
    public CalendarTask getMainTaskInstance() { return mainTaskInstance; }
    public boolean isDebugMode() { return this.debugMode;
    }
}