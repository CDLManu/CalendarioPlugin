package it.cdl.calendario;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.Arrays;
import java.util.List;

/**
 * Gestisce tutta la logica di business legata al tempo del calendario.
 * Questa classe è il "cervello" che tiene traccia della data corrente (giorno, mese, anno),
 * determina le stagioni, e gestisce il salvataggio e il caricamento dei dati temporali.
 */
public class TimeManager {

    private final CalendarioPlugin plugin;
    private int annoCorrente;
    private int meseCorrente;
    private int giornoCorrente;

    /** Lista immutabile contenente i nomi dei mesi. */
    private final List<String> nomiMesi = Arrays.asList(
            "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
            "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
    );
    /** Lista immutabile contenente il numero di giorni per ogni mese corrispondente. */
    private final List<Integer> giorniPerMese = Arrays.asList(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31);

    /**
     * Enum che definisce le quattro stagioni, ciascuna con un nome formattato per la visualizzazione.
     */
    public enum Stagione {
        INVERNO("§bInverno"),
        PRIMAVERA("§aPrimavera"),
        ESTATE("§eEstate"),
        AUTUNNO("§6Autunno");

        private final String displayName;

        Stagione(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Costruttore del TimeManager. Al momento della creazione, carica automaticamente
     * la data salvata dal file di configurazione.
     * @param plugin L'istanza principale del plugin.
     */
    public TimeManager(CalendarioPlugin plugin) {
        this.plugin = plugin;
        this.loadData();
    }

    // --- Metodi "Setter" per la modifica manuale della data (usati dai comandi) ---
    public void setGiornoCorrente(int giornoCorrente) { this.giornoCorrente = giornoCorrente; }
    public void setMeseCorrente(int meseCorrente) { this.meseCorrente = meseCorrente; }
    public void setAnnoCorrente(int annoCorrente) { this.annoCorrente = annoCorrente; }

    /**
     * Restituisce il nome formattato (con codice colore) della stagione corrente.
     * @return Il nome della stagione.
     */
    public String getStagioneCorrente() {
        return getEnumStagioneCorrente().getDisplayName();
    }

    /**
     * Determina e restituisce l'oggetto Enum della stagione corrente basandosi sul mese.
     * @return L'enum della stagione.
     */
    public Stagione getEnumStagioneCorrente() {
        return switch (meseCorrente) {
            case 12, 1, 2 -> Stagione.INVERNO;
            case 3, 4, 5 -> Stagione.PRIMAVERA;
            case 6, 7, 8 -> Stagione.ESTATE;
            default -> Stagione.AUTUNNO; // Copre i mesi 9, 10, 11
        };
    }

    // --- Metodi "Getter" per ottenere informazioni sulla data ---
    public int getGiorniNelMese(int mese) { if (mese < 1 || mese > 12) return 31; return giorniPerMese.get(mese - 1); }
    public String getNomeMese(int mese) { if (mese < 1 || mese > 12) return "Mese Invalido"; return nomiMesi.get(mese - 1); }
    public int getAnnoCorrente() { return annoCorrente; }
    public int getMeseCorrente() { return meseCorrente; }
    public int getGiornoCorrente() { return giornoCorrente; }

    /**
     * Fa avanzare il calendario di un giorno in modo silenzioso, senza notificare i giocatori.
     * Gestisce correttamente il cambio di mese e di anno.
     */
    public void advanceDaySilently() {
        giornoCorrente++;
        if (giornoCorrente > getGiorniNelMese(meseCorrente)) {
            giornoCorrente = 1;
            meseCorrente++;
            if (meseCorrente > 12) {
                meseCorrente = 1;
                annoCorrente++;
            }
        }
    }

    /**
     * Fa avanzare il calendario di un giorno e invia un messaggio a tutto il server
     * per notificare i giocatori della nuova data.
     */
    public void advanceDayWithBroadcast() {
        advanceDaySilently();
        // Utilizza l'API Adventure di Paper per creare un messaggio colorato.
        Component messaggio = Component.text("È un nuovo giorno! Data: ", NamedTextColor.GREEN)
                .append(Component.text(giornoCorrente + " " + getNomeMese(meseCorrente) + " " + annoCorrente, NamedTextColor.WHITE));
        plugin.getServer().broadcast(messaggio);
    }

    /**
     * Salva la data e l'ora del mondo corrente nel file config.yml.
     * Questo metodo viene chiamato alla disattivazione del plugin.
     */
    public void saveData() {
        plugin.getConfig().set("calendario.anno", annoCorrente);
        plugin.getConfig().set("calendario.mese", meseCorrente);
        plugin.getConfig().set("calendario.giorno", giornoCorrente);
        // Salva anche i tick totali del mondo per sincronizzare il tempo passato mentre il server era offline.
        long totalTicks = plugin.getServer().getWorlds().getFirst().getFullTime();
        plugin.getConfig().set("calendario.total-ticks-salvati", totalTicks);
        plugin.saveConfig();
        plugin.getLogger().info("Data del calendario salvata!");
    }

    /**
     * Carica i dati del calendario dal file config.yml all'avvio del plugin.
     * Se non esistono dati, inizializza il calendario.
     * Sincronizza il tempo recuperando i giorni passati mentre il server era offline.
     */
    public void loadData() {
        if (plugin.getConfig().contains("calendario.giorno")) {
            // Se esistono dati, caricali.
            annoCorrente = plugin.getConfig().getInt("calendario.anno");
            meseCorrente = plugin.getConfig().getInt("calendario.mese");
            giornoCorrente = plugin.getConfig().getInt("calendario.giorno");

            // Sincronizza il tempo passato offline.
            long savedTicks = plugin.getConfig().getLong("calendario.total-ticks-salvati", 0);
            long currentTicks = plugin.getServer().getWorlds().getFirst().getFullTime();
            long ticksPassed = currentTicks - savedTicks;

            if (ticksPassed > 0) {
                long daysToCatchUp = ticksPassed / 24000;
                if (daysToCatchUp > 0) {
                    plugin.getLogger().info("Sincronizzazione: recupero di " + daysToCatchUp + " giorni passati mentre il server era offline.");
                    for (int i = 0; i < daysToCatchUp; i++) {
                        advanceDaySilently();
                    }
                }
            }
            plugin.getLogger().info("Data caricata e sincronizzata.");
        } else {
            // Se è il primo avvio, inizializza la data e sincronizzala con i giorni già trascorsi nel mondo.
            annoCorrente = 1;
            meseCorrente = 1;
            giornoCorrente = 1;
            long currentTotalTicks = plugin.getServer().getWorlds().getFirst().getFullTime();
            long initialDays = currentTotalTicks / 24000;
            if (initialDays > 0) {
                plugin.getLogger().info("Primo avvio: sincronizzazione con i " + initialDays + " giorni già passati nel mondo.");
                for (int i = 0; i < initialDays; i++) {
                    advanceDaySilently();
                }
            }
        }
    }
}