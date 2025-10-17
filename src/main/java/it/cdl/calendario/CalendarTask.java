package it.cdl.calendario;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Rappresenta il "cuore" pulsante del plugin Calendario.
 * Questo task, eseguito a intervalli regolari, è responsabile della gestione
 * del ciclo temporale personalizzato del mondo di gioco. Le sue responsabilità includono:
 * <ul>
 * <li>Avanzamento del tempo con velocità variabile in base alla stagione e alla configurazione.</li>
 * <li>Rilevamento del passaggio dei giorni per aggiornare il calendario interno.</li>
 * <li>Innescare gli aggiornamenti per i manager dipendenti (es. {@link BossBarManager}, {@link SeasonalEffectsManager}).</li>
 * </ul>
 */
public class CalendarTask extends BukkitRunnable {

    private final CalendarioPlugin plugin;
    private final World world;
    private final TimeManager timeManager;
    private final BossBarManager bossBarManager;
    private final SeasonalEffectsManager seasonalEffectsManager;

    /**
     * Memorizza il totale dei giorni trascorsi dall'ultimo controllo per rilevare in modo affidabile
     * l'inizio di un nuovo giorno.
     */
    private long lastCheckedTotalDays;
    /**
     * Cache del mese corrente per rilevare un cambio di mese e, di conseguenza, di stagione.
     */
    private int cachedMonth;
    /**
     * Cache della stagione corrente per identificare un cambio di stagione e attivare gli effetti associati.
     */
    private TimeManager.Stagione lastCheckedSeason;

    /**
     * Il tick di Minecraft in cui il tempo passa da giorno a notte (tramonto).
     */
    private static final long SUNSET_TICKS = 13000L;
    /**
     * Il numero totale di tick in un ciclo giornaliero completo di Minecraft.
     */
    private static final long DAY_CYCLE_TICKS = 24000L;

    /**
     * La velocità di avanzamento del tempo (tick/secondo) durante il giorno.
     */
    private double dayTickRate;
    /**
     * La velocità di avanzamento del tempo (tick/secondo) durante la notte.
     */
    private double nightTickRate;
    /**
     * Accumula i valori frazionari dei tick per garantire una progressione temporale precisa,
     * prevenendo perdite di precisione dovute agli arrotondamenti.
     */
    private double tickAccumulator = 0.0;

    /**
     * Costruttore del task principale del calendario.
     * Inizializza i riferimenti hai manager, sincronizza lo stato iniziale con il mondo
     * e calcola la velocità del tempo iniziale.
     *
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

        this.lastCheckedTotalDays = world.getFullTime() / DAY_CYCLE_TICKS;
        this.lastCheckedSeason = timeManager.getEnumStagioneCorrente();
        this.cachedMonth = timeManager.getMeseCorrente();
        this.tickAccumulator = this.world.getTime() % 1.0;
        updateSeasonalSpeed();
    }

    /**
     * Resetta l'accumulatore di tick. Invocato quando il tempo viene alterato istantaneamente,
     * come dopo che i giocatori hanno dormito, per evitare salti temporali imprevisti.
     */
    public void acceptTimeSkip() {
        this.tickAccumulator = 0.0;
    }

    /**
     * Forza un ricalcolo immediato della velocità del tempo e un aggiornamento
     * dei sistemi dipendenti. Essenziale dopo modifiche manuali alla data tramite comandi.
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
     * Aggiorna le variabili {@code dayTickRate} e {@code nightTickRate} leggendo i valori
     * dal file di configurazione in base alla stagione corrente.
     * Questo metodo permette di avere cicli giorno/notte di durata variabile.
     */
    private void updateSeasonalSpeed() {
        this.cachedMonth = timeManager.getMeseCorrente();
        TimeManager.Stagione stagione = timeManager.getEnumStagioneCorrente();
        double realSecondsForDay;
        double realSecondsForNight;

        String seasonConfigKey = switch (stagione) {
            case INVERNO -> "inverno";
            case PRIMAVERA -> "primavera";
            case ESTATE -> "estate";
            case AUTUNNO -> "autunno";
        };

        realSecondsForDay = plugin.getConfig().getDouble("time-cycle." + seasonConfigKey + ".day-duration-seconds", 600.0);
        realSecondsForNight = plugin.getConfig().getDouble("time-cycle." + seasonConfigKey + ".night-duration-seconds", 600.0);

        this.dayTickRate = SUNSET_TICKS / realSecondsForDay;
        this.nightTickRate = (DAY_CYCLE_TICKS - SUNSET_TICKS) / realSecondsForNight;
    }

    /**
     * Metodo principale del task, eseguito a ogni ciclo dello scheduler.
     * Contiene la logica di avanzamento del tempo e il rilevamento del cambio di giorno/stagione.
     */
    @Override
    public void run() {
        boolean isDay = world.getTime() < SUNSET_TICKS;
        double currentTickRate = isDay ? dayTickRate : nightTickRate;
        tickAccumulator += currentTickRate;
        long ticksToApply = (long) tickAccumulator;

        if (ticksToApply > 0) {
            world.setFullTime(world.getFullTime() + ticksToApply);
            tickAccumulator -= ticksToApply;
        }

        long currentTotalDays = world.getFullTime() / DAY_CYCLE_TICKS;
        if (currentTotalDays > lastCheckedTotalDays) {
            long daysPassed = currentTotalDays - lastCheckedTotalDays;
            for (int i = 0; i < daysPassed; i++) {
                timeManager.advanceDayWithBroadcast();
                plugin.getEventManager().onNewDay();
            }
            lastCheckedTotalDays = currentTotalDays;

            int newMonth = timeManager.getMeseCorrente();
            if (newMonth != cachedMonth) {
                updateSeasonalSpeed();
                TimeManager.Stagione newSeason = timeManager.getEnumStagioneCorrente();
                if (newSeason != lastCheckedSeason) {
                    plugin.getLogger().info("La stagione è cambiata da " + lastCheckedSeason.name() + " a " + newSeason.name() + "!");
                    seasonalEffectsManager.handleSeasonChange(newSeason);
                    lastCheckedSeason = newSeason;
                }
            }
        }
        bossBarManager.updateBossBars();
    }
}