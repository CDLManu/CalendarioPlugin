package it.cdl.calendario;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Gestisce tutti gli aspetti legati al tempo e al calendario del plugin.
 * Le sue responsabilità includono:
 * <ul>
 * <li>Mantenere e modificare la data corrente (giorno, mese, anno).</li>
 * <li>Salvare e caricare la data da un file dedicato (data.yml) per separarla dalla configurazione.</li>
 * <li>Determinare la stagione corrente in base al mese.</li>
 * <li>Fornire metodi di utilità per accedere alle informazioni temporali in modo formattato.</li>
 * </ul>
 */
public class TimeManager {

    private final CalendarioPlugin plugin;

    private int giornoCorrente;
    private int meseCorrente;
    private int annoCorrente;

    /**
     * Enumerazione che definisce le quattro stagioni del calendario.
     */
    public enum Stagione {
        INVERNO, PRIMAVERA, ESTATE, AUTUNNO
    }

    /**
     * Costruttore del TimeManager.
     * Inizializza il manager e carica immediatamente i dati salvati.
     *
     * @param plugin L'istanza principale del plugin.
     */
    public TimeManager(CalendarioPlugin plugin) {
        this.plugin = plugin;
        loadData();
    }
    /**
     * Imposta il giorno corrente del calendario.
     * @param giorno Il nuovo giorno da impostare.
     */
    public void setGiorno(int giorno) {
        this.giornoCorrente = giorno;
    }

    /**
     * Imposta il mese corrente del calendario.
     * @param mese Il nuovo mese da impostare.
     */
    public void setMese(int mese) {
        this.meseCorrente = mese;
    }

    /**
     * Imposta l'anno corrente del calendario.
     * @param anno Il nuovo anno da impostare.
     */
    public void setAnno(int anno) {
        this.annoCorrente = anno;
    }

    /**
     * Restituisce il numero di giorni nel mese corrente, tenendo conto anche degli anni bisestili.
     * @return Il numero di giorni esatto per il mese e l'anno correnti.
     */
    public int getGiorniNelMese() {
        int mese = this.meseCorrente;
        int anno = this.annoCorrente;

        return switch (mese) {
            // Mesi con 31 giorni
            case 1, 3, 5, 7, 8, 10, 12 -> 31;

            // Mesi con 30 giorni
            case 4, 6, 9, 11 -> 30;

            // Caso speciale: Febbraio
            case 2 -> {
                // Un anno è bisestile se è divisibile per 4,
                // tranne se è divisibile per 100 a meno che non sia anche divisibile per 400.
                boolean isBisestile = (anno % 4 == 0 && anno % 100 != 0) || (anno % 400 == 0);
                if (isBisestile) {
                    yield 29; // Febbraio ha 29 giorni in un anno bisestile
                } else {
                    yield 28; // Altrimenti ne ha 28
                }
            }
            default -> 30; // Fallback di sicurezza, non dovrebbe mai essere raggiunto
        };
    }
    /**
     * Salva lo stato attuale del calendario (data e tempo del mondo) nel file {@code data.yml}.
     * Questo metodo viene chiamato alla disabilitazione del plugin per garantire la persistenza dei dati.
     */
    public void saveData() {
        File dataFile = new File(plugin.getDataFolder(), "data.yml");
        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        dataConfig.set("calendario.anno", this.annoCorrente);
        dataConfig.set("calendario.mese", this.meseCorrente);
        dataConfig.set("calendario.giorno", this.giornoCorrente);

        // Usa Optional per gestire in modo sicuro il caso in cui nessun mondo sia caricato.
        Bukkit.getWorlds().stream().findFirst()
                .ifPresent(world -> dataConfig.set("calendario.total-ticks-salvati", world.getFullTime()));

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare i dati del calendario in data.yml!", e);
        }
    }

    /**
     * Carica lo stato del calendario dal file {@code data.yml}.
     * Se il file non esiste, esegue una migrazione una tantum dei dati dal vecchio
     * percorso in {@code config.yml} per garantire la retro compatibilità.
     */
    private void loadData() {
        File dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.getLogger().info("File data.yml non trovato, carico i dati iniziali da config.yml per la migrazione...");
            this.annoCorrente = plugin.getConfig().getInt("calendario.anno", 1);
            this.meseCorrente = plugin.getConfig().getInt("calendario.mese", 1);
            this.giornoCorrente = plugin.getConfig().getInt("calendario.giorno", 1);

            Bukkit.getWorlds().stream().findFirst().ifPresent(world -> {
                long ticksSalvati = plugin.getConfig().getLong("calendario.total-ticks-salvati", 0L);
                world.setFullTime(ticksSalvati);
            });
            saveData();
            return;
        }

        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        this.annoCorrente = dataConfig.getInt("calendario.anno", 1);
        this.meseCorrente = dataConfig.getInt("calendario.mese", 1);
        this.giornoCorrente = dataConfig.getInt("calendario.giorno", 1);

        Bukkit.getWorlds().stream().findFirst().ifPresent(world -> {
            long ticksSalvati = dataConfig.getLong("calendario.total-ticks-salvati", 0L);
            world.setFullTime(ticksSalvati);
        });
    }

    /**
     * Fa avanzare il calendario di un giorno, gestendo il cambio di mese e anno
     * in base al numero esatto di giorni del mese corrente.
     */
    public void advanceDayWithBroadcast() {
        this.giornoCorrente++;
        // Usa il nuovo metodo per controllare se il mese è finito
        if (this.giornoCorrente > getGiorniNelMese()) {
            this.giornoCorrente = 1;
            this.meseCorrente++;
            if (this.meseCorrente > 12) {
                this.meseCorrente = 1;
                this.annoCorrente++;
            }
        }

        LanguageManager lang = plugin.getLanguageManager();
        String dateString = this.giornoCorrente + " " + getNomeMese(this.meseCorrente) + " " + this.annoCorrente;
        String legacyMessage = lang.getString("events.new-day", "{date}", dateString);

        Component messageComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyMessage);
        Bukkit.broadcast(messageComponent);
    }

    // I metodi setGiorno, setMese e setAnno sono stati rimossi perché non utilizzati.
    // Le modifiche alla data avvengono tramite il comando /calendario set, che accede
    // direttamente al TimeManager e salva i dati.

    /**
     * Restituisce il giorno corrente.
     * @return Il giorno del mese.
     */
    public int getGiornoCorrente() {
        return this.giornoCorrente;
    }

    /**
     * Restituisce il mese corrente.
     * @return Il mese dell'anno.
     */
    public int getMeseCorrente() {
        return this.meseCorrente;
    }

    /**
     * Restituisce l'anno corrente.
     * @return L'anno.
     */
    public int getAnnoCorrente() {
        return this.annoCorrente;
    }

    /**
     * Ottiene il nome tradotto di un mese dal suo numero.
     *
     * @param mese Il numero del mese (1-12).
     * @return Il nome del mese o un messaggio di errore se invalido.
     */
    public String getNomeMese(int mese) {
        return plugin.getLanguageManager().getString("months." + mese, "Mese Invalido");
    }

    /**
     * Ottiene il nome tradotto e colorato della stagione corrente.
     *
     * @return La stringa della stagione.
     */
    public String getStagioneCorrente() {
        return plugin.getLanguageManager().getString("seasons." + getEnumStagioneCorrente().name());
    }

    /**
     * Determina e restituisce l'enumerazione {@link Stagione} in base al mese corrente.
     *
     * @return L'enum della stagione corrente.
     */
    public Stagione getEnumStagioneCorrente() {
        return switch (this.meseCorrente) {
            case 12, 1, 2 -> Stagione.INVERNO;
            case 3, 4, 5 -> Stagione.PRIMAVERA;
            case 6, 7, 8 -> Stagione.ESTATE;
            case 9, 10, 11 -> Stagione.AUTUNNO;
            default -> Stagione.PRIMAVERA; // Fallback
        };
    }
}