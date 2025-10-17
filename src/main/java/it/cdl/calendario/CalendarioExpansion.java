package it.cdl.calendario;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gestisce l'integrazione con il plugin PlaceholderAPI.
 * Questa classe permette di utilizzare i dati del CalendarioPlugin (come data, stagione, ora)
 * in altri plugin che supportano PlaceholderAPI (es. Scoreboard, chat, tablist).
 */
public class CalendarioExpansion extends PlaceholderExpansion {

    /** Un riferimento all'istanza principale del plugin per accedere ai suoi dati.
     */
    private final CalendarioPlugin plugin;

    /**
     * Costruttore della classe di espansione.
     * @param plugin L'istanza principale di CalendarioPlugin.
     */
    public CalendarioExpansion(CalendarioPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Restituisce l'identificatore univoco di questa espansione.
     * I placeholder verranno richiamati usando questo nome (es. %calendario_day%).
     * @return L'identificatore "calendario".
     */
    @Override
    public @NotNull String getIdentifier() {
        return "calendario";
    }

    /**
     * Restituisce il nome dell'autore del plugin.
     * @return Il nome dell'autore.
     */
    @Override
    public @NotNull String getAuthor() {
        return "ManuX";
    }

    /**
     * Restituisce la versione corrente del plugin.
     * Il valore viene letto dinamicamente dal file plugin.yml.
     * @return La stringa della versione.
     */
    @SuppressWarnings("deprecation")
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Indica a PlaceholderAPI se l'espansione deve essere persistente (caricata all'avvio).
     * Per la maggior parte delle espansioni, questo dovrebbe essere true per garantire il funzionamento.
     * @return true.
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * Metodo principale chiamato da PlaceholderAPI quando deve risolvere un placeholder
     * che inizia con %calendario_...%.
     * @param player Il giocatore per cui il placeholder viene richiesto (può essere null).
     * @param identifier La parte del placeholder dopo l'identificatore (es. "day", "season").
     * @return Il valore sostituito come stringa, o null se l'identificatore non è valido.
     */
    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        // Ottiene un'istanza del TimeManager per accedere ai dati del calendario.
        TimeManager timeManager = plugin.getTimeManager();
        if (timeManager == null) {
            // Se il TimeManager non è ancora pronto, restituisce un messaggio di errore.
            return plugin.getLanguageManager().getString("errors.placeholder-api-fail");
        }

        // Utilizza uno switch per gestire in modo efficiente i diversi placeholder richiesti.
        return switch (identifier) {
            // %calendario_time%: Restituisce l'ora di gioco corrente in formato HH:MM.
            case "time" -> {
                World world = plugin.getServer().getWorlds().getFirst();
                long time = world.getTime();
                long ore = (time / 1000 + 6) % 24;
                long minuti = (long) ((time % 1000) / 1000.0 * 60);
                yield String.format("%02d:%02d", ore, minuti);
            }
            // %calendario_day%: Restituisce il giorno corrente del calendario.
            case "day" -> String.valueOf(timeManager.getGiornoCorrente());
            // %calendario_month%: Restituisce il nome del mese corrente.
            case "month" -> timeManager.getNomeMese(timeManager.getMeseCorrente());
            // %calendario_year%: Restituisce l'anno corrente.
            case "year" -> String.valueOf(timeManager.getAnnoCorrente());
            // %calendario_season%: Restituisce il nome della stagione corrente, rimuovendo i codici colore.
            case "season" -> timeManager.getStagioneCorrente().replaceAll("§.", "");
            // Se l'identificatore non corrisponde a nessun caso, restituisce null.
            default -> null;
        };
    }
}