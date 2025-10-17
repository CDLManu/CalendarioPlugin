package it.cdl.calendario;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Gestisce il caricamento, l'attivazione e la terminazione di eventi personalizzati.
 * Legge gli eventi dal file events.yml e controlla ogni giorno se le condizioni
 * per l'avvio di un nuovo evento sono soddisfatte.
 */
public class EventManager {

    private final CalendarioPlugin plugin;
    private final Map<String, CustomEvent> loadedEvents = new HashMap<>();
    private final Random random = new Random();

    private CustomEvent activeEvent = null;
    private int daysRemaining = 0;

    public EventManager(CalendarioPlugin plugin) {
        this.plugin = plugin;
        loadEvents();
    }

    /**
     * Carica tutti gli eventi definiti nel file events.yml e li memorizza in una mappa.
     */
    private void loadEvents() {
        File eventsFile = new File(plugin.getDataFolder(), "events.yml");
        if (!eventsFile.exists()) {
            plugin.saveResource("events.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(eventsFile);
        ConfigurationSection eventsSection = config.getConfigurationSection("events");
        if (eventsSection == null) return;

        for (String eventId : eventsSection.getKeys(false)) {
            ConfigurationSection eventData = eventsSection.getConfigurationSection(eventId);
            if (eventData == null) continue;

            CustomEvent event = new CustomEvent(
                    eventId.toLowerCase(),
                    eventData.getString("display-name", "Evento Senza Nome"),
                    eventData.getString("type", "RANDOM").toUpperCase(),
                    eventData.getString("trigger-date", ""),
                    eventData.getInt("conditions.chance", 0),
                    new HashSet<>(eventData.getStringList("conditions.seasons")),
                    eventData.getInt("duration-days", 1),
                    eventData.getStringList("start-commands"),
                    eventData.getStringList("end-commands")
            );
            loadedEvents.put(eventId.toLowerCase(), event);
        }
        plugin.getLogger().info("Caricati " + loadedEvents.size() + " eventi personalizzati da events.yml.");
    }

    /**
     * Metodo chiamato all'inizio di un nuovo giorno.
     * Gestisce la durata dell'evento attivo o controlla se un nuovo evento deve iniziare.
     */
    public void onNewDay() {
        // --- LOGICA COMPLETAMENTE RISCRITTA ---

        // Fase 1: Gestire l'evento attualmente attivo.
        if (activeEvent != null) {
            // Decrementa la durata se l'evento non è infinito.
            if (daysRemaining > 0) {
                daysRemaining--;
            }

            // Controlla se l'evento è terminato.
            if (daysRemaining == 0 && activeEvent.durationDays() != -1) {
                endActiveEvent();
            }
        }

        // Fase 2: Se NON c'è nessun evento attivo (o è appena terminato), controlla se ne deve iniziare uno nuovo.
        if (activeEvent == null) {
            TimeManager tm = plugin.getTimeManager();
            for (CustomEvent event : loadedEvents.values()) {
                if (shouldEventStart(event, tm)) {
                    startEvent(event);
                    break; // Avvia al massimo un evento al giorno.
                }
            }
        }
    }

    /**
     * Gestisce un cambio di data manuale tramite comando.
     * Termina qualsiasi evento attivo e controlla se un nuovo evento deve iniziare nella nuova data.
     */
    public void handleDateChange() {
        if (activeEvent != null) {
            plugin.getLogger().info("Cambio data manuale: l'evento '" + activeEvent.displayName() + "' viene terminato forzatamente.");
            endActiveEvent();
        }
        onNewDay();
    }

    /**
     * Controlla se un evento soddisfa le condizioni per essere avviato nella data corrente.
     * @param event L'evento da controllare.
     * @param tm Un riferimento al TimeManager.
     * @return true se l'evento deve iniziare, altrimenti false.
     */
    private boolean shouldEventStart(CustomEvent event, TimeManager tm) {
        int currentDay = tm.getGiornoCorrente();
        int currentMonth = tm.getMeseCorrente();
        int currentYear = tm.getAnnoCorrente();

        return switch (event.type()) {
            case "FIXED_DATE" -> {
                String[] parts = event.triggerDate().split("/");
                yield parts.length == 3 &&
                        Integer.parseInt(parts[0]) == currentDay &&
                        Integer.parseInt(parts[1]) == currentMonth &&
                        Integer.parseInt(parts[2]) == currentYear;
            }
            case "ANNUAL" -> {
                String[] parts = event.triggerDate().split("/");
                yield parts.length == 2 &&
                        Integer.parseInt(parts[0]) == currentDay &&
                        Integer.parseInt(parts[1]) == currentMonth;
            }
            case "RANDOM" -> {
                TimeManager.Stagione currentSeason = tm.getEnumStagioneCorrente();
                boolean seasonMatch = event.seasons().isEmpty() || event.seasons().contains(currentSeason.name());
                yield seasonMatch && random.nextInt(100) < event.chance();
            }
            default -> false;
        };
    }

    /**
     * Avvia un evento, eseguendo i suoi comandi di inizio.
     * @param event L'evento da avviare.
     */
    private void startEvent(CustomEvent event) {
        this.activeEvent = event;
        this.daysRemaining = event.durationDays();

        String displayName = event.displayName().replace('&', '§');
        plugin.getLogger().info("Evento iniziato: " + displayName);

        executeCommands(event.startCommands());
    }

    /**
     * Termina l'evento attualmente attivo, eseguendo i suoi comandi di fine.
     */
    public void endActiveEvent() {
        if (activeEvent == null) return;

        String displayName = activeEvent.displayName().replace('&', '§');
        plugin.getLogger().info("Evento terminato: " + displayName);

        executeCommands(activeEvent.endCommands());

        this.activeEvent = null;
        this.daysRemaining = 0;
    }

    /**
     * Esegue una lista di comandi dalla console.
     * @param commands La lista di comandi da eseguire.
     */
    private void executeCommands(List<String> commands) {
        for (String cmd : commands) {
            // --- CORREZIONE DEL TYPO BUKKICK -> BUKKIT ---
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /**
     * Forza l'avvio di un evento tramite comando.
     * @param eventId L'ID dell'evento da avviare.
     * @return true se l'evento è stato trovato e avviato, altrimenti false.
     */
    public boolean forceStartEvent(String eventId) {
        CustomEvent event = loadedEvents.get(eventId.toLowerCase());
        if (event != null) {
            if (activeEvent != null) {
                endActiveEvent();
            }
            startEvent(event);
            return true;
        }
        return false;
    }

    /**
     * Forza la terminazione dell'evento attivo tramite comando.
     * @return true se un evento era attivo ed è stato terminato, altrimenti false.
     */
    public boolean forceEndActiveEvent() {
        if (activeEvent != null) {
            endActiveEvent();
            return true;
        }
        return false;
    }

    public CustomEvent getActiveEvent() {
        return activeEvent;
    }
}