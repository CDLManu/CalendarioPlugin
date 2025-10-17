package it.cdl.calendario;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestisce la creazione, l'aggiornamento e la rimozione delle Boss Bar per i giocatori.
 * La Boss Bar mostra informazioni dinamiche come data, stagione, meteo e orario del gioco.
 * Utilizza un sistema di caching per ottimizzare le performance, aggiornando solo le parti
 * necessarie del testo quando i dati cambiano.
 */
public class BossBarManager {

    private final CalendarioPlugin plugin;
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();

    /**
     * Flag per abilitare o disabilitare completamente la funzionalità della Boss Bar.
     * Caricato dal file config.yml.
     */
    private final boolean isBossBarEnabled;

    /**
     * Flag per mostrare o nascondere la barra di progresso colorata.
     * Caricato dal file config.yml.
     */
    private final boolean showProgressBar;

    /**
     * Cache per la parte statica del titolo della Boss Bar (data, stagione, meteo).
     * Viene ricalcolata solo quando uno di questi elementi cambia, per evitare
     * di ricostruire la stringa a ogni tick.
     */
    private String cachedTitlePrefix = "";

    /**
     * Variabili di stato per tracciare l'ultimo stato noto del mondo di gioco.
     * Servono a determinare quando è necessario aggiornare la cache del titolo.
     */
    private int lastCheckedDay = -1;
    private int lastCheckedMonth = -1;
    private int lastCheckedYear = -1;
    private boolean wasThundering = false;
    private boolean wasRaining = false;

    /**
     * Costruttore del manager della Boss Bar.
     * Inizializza le impostazioni leggendole dal file di configurazione del plugin.
     *
     * @param plugin L'istanza principale del plugin CalendarioPlugin.
     */
    public BossBarManager(CalendarioPlugin plugin) {
        this.plugin = plugin;
        this.isBossBarEnabled = plugin.getConfig().getBoolean("bossbar.enabled", true);
        this.showProgressBar = plugin.getConfig().getBoolean("bossbar.show-progress-bar", true);
    }

    /**
     * Metodo principale per l'aggiornamento, chiamato periodicamente dal task principale.
     * Controlla se i dati visualizzati (data, meteo) sono cambiati. Se lo sono,
     * rigenera il prefisso del titolo. Successivamente, aggiorna la parte dinamica (orario)
     * e applica il titolo finale e la progressione a tutte le Boss Bar attive.
     */
    public void updateBossBars() {
        if (!isBossBarEnabled) return;

        TimeManager tm = plugin.getTimeManager();
        World world = Bukkit.getWorlds().getFirst();
        if (world == null) return; // Prevenzione errori se il mondo non è caricato

        boolean needsPrefixUpdate = false;
        int currentDay = tm.getGiornoCorrente();
        int currentMonth = tm.getMeseCorrente();
        int currentYear = tm.getAnnoCorrente();
        boolean isThundering = world.isThundering();
        boolean isRaining = world.hasStorm();

        if (currentDay != lastCheckedDay || currentMonth != lastCheckedMonth || currentYear != lastCheckedYear) {
            lastCheckedDay = currentDay;
            lastCheckedMonth = currentMonth;
            lastCheckedYear = currentYear;
            needsPrefixUpdate = true;
        }

        if (isThundering != wasThundering || isRaining != wasRaining || cachedTitlePrefix.isEmpty()) {
            wasThundering = isThundering;
            wasRaining = isRaining;
            needsPrefixUpdate = true;
        }

        if (needsPrefixUpdate) {
            LanguageManager lang = plugin.getLanguageManager();

            String datePart = "§a" + currentDay + " " + tm.getNomeMese(currentMonth) + " " + currentYear;
            String seasonPart = tm.getStagioneCorrente();
            String weatherPart = isThundering ? lang.getString("bossbar.weather-storm") : (isRaining ? lang.getString("bossbar.weather-rain") : lang.getString("bossbar.weather-clear"));

            String format = plugin.getConfig().getString("bossbar.format", "&a{data} &8| {stagione} &8| {meteo} &8| &e{ora}");
            this.cachedTitlePrefix = format
                    .replace("{data}", datePart)
                    .replace("{stagione}", seasonPart)
                    .replace("{meteo}", weatherPart)
                    .replace('&', '§');
        }

        long time = world.getTime();
        String orarioPart = String.format("%02d:%02d", (time / 1000 + 6) % 24, (long) ((time % 1000) / 1000.0 * 60));
        String titoloFinale = this.cachedTitlePrefix.replace("{ora}", orarioPart);

        double progress = showProgressBar ? (time / 24000.0) : 0.0;

        for (BossBar bossBar : playerBossBars.values()) {
            bossBar.setTitle(titoloFinale);
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
        }
    }

    /**
     * Aggiunge un giocatore al sistema della Boss Bar.
     * Crea una nuova Boss Bar personalizzata per il giocatore e la visualizza.
     * Viene tipicamente chiamato all'evento di join del giocatore.
     *
     * @param player Il giocatore a cui mostrare la Boss Bar.
     */
    public void addPlayer(Player player) {
        if (!isBossBarEnabled) return;

        BarColor color;
        BarStyle style;
        try {
            color = BarColor.valueOf(plugin.getConfig().getString("bossbar.bar-color", "BLUE").toUpperCase());
        } catch (IllegalArgumentException e) {
            color = BarColor.BLUE;
        }
        try {
            style = BarStyle.valueOf(plugin.getConfig().getString("bossbar.bar-style", "SOLID").toUpperCase());
        } catch (IllegalArgumentException e) {
            style = BarStyle.SOLID;
        }

        BossBar bossBar = Bukkit.createBossBar("", color, style);
        bossBar.addPlayer(player);
        playerBossBars.put(player.getUniqueId(), bossBar);
    }

    /**
     * Rimuove un giocatore dal sistema della Boss Bar.
     * Nasconde e distrugge la Boss Bar associata al giocatore.
     * Viene tipicamente chiamato all'evento di quit del giocatore.
     *
     * @param player Il giocatore da cui rimuovere la Boss Bar.
     */
    public void removePlayer(Player player) {
        BossBar bossBar = playerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * Rimuove tutti i giocatori e distrugge tutte le Boss Bar attive.
     * Utilizzato durante la disabilitazione o il ricaricamento del plugin
     * per garantire una pulizia completa.
     */
    public void removeAllPlayers() {
        playerBossBars.values().forEach(BossBar::removeAll);
        playerBossBars.clear();
    }
}