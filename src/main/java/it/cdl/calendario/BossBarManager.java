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
 * Gestisce tutti gli aspetti della Boss Bar informativa, inclusa la sua creazione,
 * aggiornamento e rimozione. È ottimizzato per avere un impatto minimo sulle performance
 * del server attraverso un sistema di caching intelligente.
 */
public class BossBarManager {

    private final CalendarioPlugin plugin;
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();

    /** Memorizza se la Boss Bar è abilitata globalmente, per evitare controlli ripetuti sul config. */
    private final boolean isBossBarEnabled;
    /** Memorizza se la barra di progressione deve essere visualizzata, per scopi puramente estetici. */
    private final boolean showProgressBar;

    // --- Campi per il Caching del Titolo ---
    // Queste variabili evitano di ricalcolare l'intero titolo della Boss Bar a ogni tick.
    private String cachedTitlePrefix = "";
    private int lastCheckedDay = -1;
    private int lastCheckedMonth = -1;
    private int lastCheckedYear = -1;
    private boolean wasThundering = false;
    private boolean wasRaining = false;

    private static final String WEATHER_STORM = "§3Tempesta";
    private static final String WEATHER_RAIN = "§9Pioggia";
    private static final String WEATHER_CLEAR = "§bSereno";

    /**
     * Costruttore della classe.
     * Inizializza i riferimenti e mette in cache le impostazioni della Boss Bar
     * lette dal file di configurazione per un accesso efficiente.
     * @param plugin L'istanza principale di CalendarioPlugin.
     */
    public BossBarManager(CalendarioPlugin plugin) {
        this.plugin = plugin;
        this.isBossBarEnabled = plugin.getConfig().getBoolean("bossbar.enabled", true);
        this.showProgressBar = plugin.getConfig().getBoolean("bossbar.show-progress-bar", true);
    }

    /**
     * Metodo principale, chiamato ogni secondo dal CalendarTask.
     * Aggiorna in modo efficiente il titolo e la progressione di tutte le Boss Bar attive.
     */
    public void updateBossBars() {
        if (!isBossBarEnabled) return;

        TimeManager tm = plugin.getTimeManager();
        World world = Bukkit.getWorlds().getFirst();

        // Determina se è necessario ricalcolare la parte "statica" del titolo.
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

        // Se necessario, ricalcola il prefisso del titolo. Questa è l'operazione "costosa"
        // che viene eseguita solo quando i dati cambiano.
        if (needsPrefixUpdate) {
            String datePart = "§a" + currentDay + " " + tm.getNomeMese(currentMonth) + " " + currentYear;
            String seasonPart = tm.getStagioneCorrente();
            String weatherPart = isThundering ? WEATHER_STORM : (isRaining ? WEATHER_RAIN : WEATHER_CLEAR);
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

        // Determina la progressione della barra in base alla configurazione.
        double progress = showProgressBar ? (time / 24000.0) : 0.0;

        for (BossBar bossBar : playerBossBars.values()) {
            bossBar.setTitle(titoloFinale);
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
        }
    }

    /**
     * Crea e mostra una nuova Boss Bar a un giocatore, se la funzionalità è abilitata.
     * @param player Il giocatore a cui aggiungere la Boss Bar.
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
     * Rimuove la Boss Bar di un giocatore quando si disconnette, per prevenire memory leak.
     * @param player Il giocatore da cui rimuovere la Boss Bar.
     */
    public void removePlayer(Player player) {
        BossBar bossBar = playerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * Rimuove tutte le Boss Bar da tutti i giocatori.
     * Utilizzato durante il reload o la disattivazione del plugin.
     */
    public void removeAllPlayers() {
        playerBossBars.values().forEach(BossBar::removeAll);
        playerBossBars.clear();
    }
}