package it.cdl.calendario;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Rappresenta il "cuore" pulsante del plugin.
 * Questo task, eseguito ogni secondo, è responsabile di:
 * 1. Gestire l'avanzamento personalizzato del tempo nel mondo.
 * 2. Rilevare il passaggio dei giorni per aggiornare il calendario.
 * 3. Innescare gli aggiornamenti per gli altri sistemi (BossBar, stagioni).
 */
public class CalendarTask extends BukkitRunnable {

    // --- Riferimenti ai componenti del plugin ---
    private final CalendarioPlugin plugin;
    private final World world;
    private final TimeManager timeManager;
    private final BossBarManager bossBarManager;
    private final SeasonalEffectsManager seasonalEffectsManager;

    // --- Stato interno del task ---
    /** Il numero totale di giorni di Minecraft trascorsi dall'ultimo controllo. Serve per rilevare quando un nuovo giorno è iniziato. */
    private long lastCheckedTotalDays;
    /** Il mese corrente, memorizzato per rilevare quando avviene un cambio di mese. */
    private int cachedMonth;
    /** La stagione corrente, memorizzata per rilevare quando avviene un cambio di stagione. */
    private TimeManager.Stagione lastCheckedSeason;

    // --- Costanti di tempo di Minecraft ---
    /** Il tick in cui inizia il tramonto in Minecraft (sera). */
    private static final long SUNSET_TICKS = 13000L;
    /** Il numero totale di tick in un giorno completo di Minecraft. */
    private static final long DAY_CYCLE_TICKS = 24000L;

    // --- Variabili per la velocità del tempo ---
    /** La quantità di tick da aggiungere al secondo durante il giorno, calcolata in base alla configurazione. */
    private double dayTickRate;
    /** La quantità di tick da aggiungere al secondo durante la notte. */
    private double nightTickRate;
    /** Accumula i tick frazionari tra un'esecuzione e l'altra per garantire una precisione temporale perfetta. */
    private double tickAccumulator = 0.0;

    /**
     * Costruttore del task.
     * Inizializza tutti i riferimenti e le variabili di stato.
     * @param plugin L'istanza principale del plugin.
     */
    public CalendarTask(CalendarioPlugin plugin) {
        this.plugin = plugin;
        this.world = plugin.getServer().getWorlds().getFirst();
        this.timeManager = plugin.getTimeManager();
        this.bossBarManager = plugin.getBossBarManager();
        this.seasonalEffectsManager = plugin.getSeasonalEffectsManager();
        if (this.world == null) {
            plugin.getLogger().severe("Nessun mondo trovato! Il task del Calendario è stato annullato.");
            this.cancel();
            return;
        }
        // Sincronizza lo stato iniziale del task con lo stato attuale del mondo.
        this.lastCheckedTotalDays = world.getFullTime() / DAY_CYCLE_TICKS;
        this.lastCheckedSeason = timeManager.getEnumStagioneCorrente();
        this.cachedMonth = timeManager.getMeseCorrente();
        this.tickAccumulator = this.world.getTime() % 1.0;
        // Calcola la velocità del tempo iniziale.
        updateSeasonalSpeed();
    }

    /**
     * Metodo chiamato dal SleepListener per notificare al task che il tempo è stato
     * saltato a causa del sonno. Resetta l'accumulatore per ripartire fluidamente.
     */
    public void acceptTimeSkip() {
        this.tickAccumulator = 0.0;
    }

    /**
     * Forza un aggiornamento immediato della velocità del tempo e della BossBar.
     * Utile dopo comandi manuali come /calendario set.
     */
    public void forceUpdate() {
        updateSeasonalSpeed();
        TimeManager.Stagione newSeason = timeManager.getEnumStagioneCorrente();
        if (newSeason != lastCheckedSeason) {
            plugin.getLogger().info("Stagione cambiata manualmente a " + newSeason.name() + "!");
            seasonalEffectsManager.handleSeasonChange(newSeason);
            lastCheckedSeason = newSeason;
        }
        bossBarManager.updateBossBars();
    }

    /**
     * Legge la durata del giorno e della notte dalla configurazione in base alla stagione
     * corrente e ricalcola la velocità di avanzamento del tempo (tick per secondo).
     */
    private void updateSeasonalSpeed() {
        this.cachedMonth = timeManager.getMeseCorrente();
        TimeManager.Stagione stagione = timeManager.getEnumStagioneCorrente();
        String seasonName = stagione.name().toLowerCase();
        double realSecondsForDay = plugin.getConfig().getDouble("time-cycle." + seasonName + ".day-duration-seconds", 600.0);
        double realSecondsForNight = plugin.getConfig().getDouble("time-cycle." + seasonName + ".night-duration-seconds", 600.0);
        this.dayTickRate = SUNSET_TICKS / realSecondsForDay;
        this.nightTickRate = (DAY_CYCLE_TICKS - SUNSET_TICKS) / realSecondsForNight;
    }

    /**
     * Metodo principale eseguito ogni secondo (20 tick di gioco).
     * Contiene la logica fondamentale del plugin.
     */
    @Override
    public void run() {
        // 1. Logica di avanzamento del tempo personalizzato
        boolean isDay = world.getTime() < SUNSET_TICKS;
        double currentTickRate = isDay ? dayTickRate : nightTickRate;
        tickAccumulator += currentTickRate;
        long ticksToApply = (long) tickAccumulator;

        if (ticksToApply > 0) {
            world.setFullTime(world.getFullTime() + ticksToApply);
            tickAccumulator -= ticksToApply; // Sottrae solo la parte intera applicata.
        }

        // 2. Logica centralizzata di avanzamento del giorno
        long currentTotalDays = world.getFullTime() / DAY_CYCLE_TICKS;
        if (currentTotalDays > lastCheckedTotalDays) {
            // Questo blocco ora gestisce tutti i passaggi di giorno (naturale, sonno, comandi).
            long daysPassed = currentTotalDays - lastCheckedTotalDays;
            for (int i = 0; i < daysPassed; i++) {
                timeManager.advanceDayWithBroadcast();
                plugin.getEventManager().onNewDay(); // <-- NUOVA RIGA
            }
            lastCheckedTotalDays = currentTotalDays; // Aggiorna il contatore.

            // 3. Controlla se è cambiato anche il mese (e la stagione)
            int newMonth = timeManager.getMeseCorrente();
            if (newMonth != cachedMonth) {
                updateSeasonalSpeed(); // Ricalcola la velocità del tempo.
                TimeManager.Stagione newSeason = timeManager.getEnumStagioneCorrente();
                if (newSeason != lastCheckedSeason) {
                    plugin.getLogger().info("La stagione è cambiata da " + lastCheckedSeason.name() + " a " + newSeason.name() + "!");
                    seasonalEffectsManager.handleSeasonChange(newSeason);
                    lastCheckedSeason = newSeason;
                }
            }
        }

        // 4. Aggiorna sempre la BossBar per mostrare l'ora corretta.
        bossBarManager.updateBossBars();
    }
}