package it.cdl.calendario;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Gestisce la meccanica dell'agricoltura stagionale.
 * Questo listener intercetta ogni tentativo di crescita delle piante e,
 * in base alla stagione corrente, può impedirne lo sviluppo.
 */
public final class CropGrowthListener implements Listener {

    private final CalendarioPlugin plugin;

    // --- Definizioni delle Piante Stagionali (lette dal config) ---
    private final Set<Material> colturePrimaverili;
    private final Set<Material> coltureEstive;
    private final Set<Material> coltureAutunnali;
    private final Set<Material> coltureInvernali;


    /**
     * Costruttore della classe.
     * Carica le definizioni delle colture stagionali dal config.yml.
     * @param plugin L'istanza principale di CalendarioPlugin.
     */
    public CropGrowthListener(CalendarioPlugin plugin) {
        this.plugin = plugin;

        // Carica le colture dal config.yml
        this.colturePrimaverili = loadCropsFromConfig("seasonal-farming.crops.primavera");
        this.coltureEstive = loadCropsFromConfig("seasonal-farming.crops.estate");
        this.coltureAutunnali = loadCropsFromConfig("seasonal-farming.crops.autunno");
        this.coltureInvernali = loadCropsFromConfig("seasonal-farming.crops.inverno");
    }

    /**
     * Metodo helper per caricare una lista di materiali dal config.
     * @param configPath Il percorso nel config.yml (es. "seasonal-farming.crops.spring")
     * @return Un Set di Materiali validi.
     */
    private Set<Material> loadCropsFromConfig(String configPath) {
        List<String> cropNames = plugin.getConfig().getStringList(configPath);
        Set<Material> materials = EnumSet.noneOf(Material.class);

        for (String name : cropNames) {
            Material mat = Material.matchMaterial(name.toUpperCase());
            if (mat != null) {
                materials.add(mat);
            } else {
                plugin.getLogger().warning(
                        plugin.getLanguageManager().getString("logs.invalid-config-material", "{material}", name)
                );
            }
        }
        return materials;
    }

    /**
     * Metodo chiamato da Bukkit ogni volta che un blocco tenta di crescere (es. Una pianta).
     * @param event L'evento di crescita del blocco.
     */
    @EventHandler
    public void onCropGrow(BlockGrowEvent event) {
        TimeManager timeManager = plugin.getTimeManager();
        TimeManager.Stagione stagioneCorrente = timeManager.getEnumStagioneCorrente();

        // Controlla se la pianta che sta crescendo è adatta alla stagione corrente.
        if (!isCropInSeason(event.getBlock().getType(), stagioneCorrente)) {
            // Se la pianta è fuori stagione, legge la probabilità di crescita dal file di configurazione.
            double chanceDiCrescere = plugin.getConfig().getDouble("seasonal-farming.out-of-season-growth-chance", 0.25);

            // Genera un numero casuale tra 0.0 e 1.0.
            // Se è maggiore della probabilità consentita,
            // la crescita viene annullata.
            if (Math.random() > chanceDiCrescere) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Metodo di utilità per determinare se un tipo di pianta è "di stagione".
     * @param cropType Il Material della pianta che sta crescendo.
     * @param stagione La stagione corrente.
     * @return true se la pianta può crescere in questa stagione, altrimenti false.
     */
    private boolean isCropInSeason(Material cropType, TimeManager.Stagione stagione) {
        // Utilizza uno switch expression per restituire in modo conciso il risultato del controllo.
        return switch (stagione) {
            case PRIMAVERA -> colturePrimaverili.contains(cropType);
            case ESTATE -> coltureEstive.contains(cropType);
            case AUTUNNO -> coltureAutunnali.contains(cropType);
            case INVERNO -> coltureInvernali.contains(cropType);
            // default non è necessario se tutti i casi Enum sono coperti
        };
    }
}