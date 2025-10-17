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
 * Gestisce il ciclo di vita degli eventi personalizzati del server.
 * Questa classe è responsabile del caricamento degli eventi dal file {@code events.yml},
 * della verifica delle condizioni di attivazione al cambio del giorno e dell'esecuzione
 * dei comandi associati all'inizio e alla fine di un evento.
 */
public class EventManager {

    private final CalendarioPlugin plugin;
    private final Map<String, CustomEvent> loadedEvents = new HashMap<>();
    private final Random random = new Random();

    /**
     * L'evento attualmente attivo sul server.
     * È {@code null} se nessun evento è in corso.
     */
    private CustomEvent activeEvent = null;
    /**
     * Il numero di giorni rimanenti prima della conclusione dell'evento attivo.
     */
    private int daysRemaining = 0;

    /**
     * Costruttore dell'EventManager.
     * Inizializza il manager e avvia il caricamento degli eventi.
     *
     * @param plugin L'istanza principale del plugin.
     */
    public EventManager(CalendarioPlugin plugin) {
        this.plugin = plugin;
        loadEvents();
    }

    /**
     * Carica e convalida tutti gli eventi definiti nel file {@code events.yml}.
     * Se il file non esiste, viene creato a partire dalle risorse del plugin.
     * Ogni evento viene mappato in un oggetto {@link CustomEvent} e memorizzato.
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
                    eventData.getString("display-name", "Nameless Event"),
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
        // MODIFICA: Usa il LanguageManager per il log
        plugin.getLogger().info(plugin.getLanguageManager().getString(
                "logs.events-loaded", "{count}", String.valueOf(loadedEvents.size())
        ));
    }

    /**
     * Metodo principale chiamato all'inizio di un nuovo giorno.
     * Gestisce il countdown della durata dell'evento attivo e, se termina, lo conclude.
     * Se nessun evento è attivo, controlla se le condizioni per l'avvio di un nuovo
     * evento sono soddisfatte.
     */
    public void onNewDay() {
        if (activeEvent != null) {
            if (daysRemaining > 0) {
                daysRemaining--;
            }
            // Conclude l'evento solo se la durata non è infinita (-1)
            if (daysRemaining == 0 && activeEvent.durationDays() != -1) {
                endActiveEvent();
            }
        }
        // Se, dopo il controllo, non c'è un evento attivo, prova ad avviarne uno nuovo.
        if (activeEvent == null) {
            TimeManager tm = plugin.getTimeManager();
            for (CustomEvent event : loadedEvents.values()) {
                if (shouldEventStart(event, tm)) {
                    startEvent(event);
                    break; // Avvia solo un evento al giorno
                }
            }
        }
    }

    /**
     * Gestisce la logica da eseguire quando la data viene modificata manualmente tramite comando.
     * Termina forzatamente l'evento attivo e riesegue il controllo di inizio giornata.
     */
    public void handleDateChange() {
        LanguageManager lang = plugin.getLanguageManager();
        if (activeEvent != null) {
            plugin.getLogger().info(lang.getString("events.date-change-end", "{eventName}", activeEvent.displayName()));
            endActiveEvent();
        }
        onNewDay(); // Simula un nuovo giorno per ricalibrare gli eventi
    }

    /**
     * Valuta se un dato evento debba iniziare in base alla data corrente e al tipo di evento.
     *
     * @param event L'evento da controllare.
     * @param tm    Il TimeManager per ottenere la data e la stagione correnti.
     * @return {@code true} se l'evento deve iniziare, altrimenti {@code false}.
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
     * Avvia un evento, impostandolo come attivo ed eseguendone i comandi di inizio.
     *
     * @param event L'evento da avviare.
     */
    public void startEvent(CustomEvent event) {
        this.activeEvent = event;
        this.daysRemaining = event.durationDays();

        String displayName = event.displayName().replace('&', '§');
        plugin.getLogger().info(plugin.getLanguageManager().getString("events.event-started-log", "{eventName}", displayName));

        executeCommands(event.startCommands());
    }

    /**
     * Termina l'evento attualmente attivo, eseguendone i comandi di fine e resettando lo stato.
     */
    public void endActiveEvent() {
        if (activeEvent == null) return;
        String displayName = activeEvent.displayName().replace('&', '§');
        plugin.getLogger().info(plugin.getLanguageManager().getString("events.event-ended-log", "{eventName}", displayName));

        executeCommands(activeEvent.endCommands());

        this.activeEvent = null;
        this.daysRemaining = 0;
    }

    /**
     * Esegue una lista di comandi tramite la console del server.
     *
     * @param commands La lista di stringhe di comandi da eseguire.
     */
    private void executeCommands(List<String> commands) {
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /**
     * Restituisce l'evento attualmente attivo.
     * @return Il CustomEvent attivo, o null se non c'è nessun evento in corso.
     */
    public CustomEvent getActiveEvent() {
        return activeEvent;
    }

    /**
     * Cerca un evento caricato tramite il suo ID.
     * @param eventId L'ID (chiave) dell'evento (es. "halloween")
     * @return Il CustomEvent, o null se non trovato.
     */
    public CustomEvent getEventById(String eventId) {
        return loadedEvents.get(eventId.toLowerCase());
    }
}