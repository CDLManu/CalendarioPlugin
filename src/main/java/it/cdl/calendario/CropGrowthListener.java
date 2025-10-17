package it.cdl.calendario;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.Set;

/**
 * Gestisce la meccanica dell'agricoltura stagionale.
 * Questo listener intercetta ogni tentativo di crescita delle piante e,
 * in base alla stagione corrente, può impedirne lo sviluppo.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class CropGrowthListener implements Listener {

    /** Un riferimento all'istanza principale del plugin per accedere ai suoi dati (config, TimeManager). */
    private final CalendarioPlugin plugin;

    /**
     * Costruttore della classe.
     * @param plugin L'istanza principale di CalendarioPlugin.
     */
    public CropGrowthListener(CalendarioPlugin plugin) {
        this.plugin = plugin;
    }

    // --- Definizioni delle Piante Stagionali ---
    // L'uso di Set garantisce ricerche estremamente veloci (complessità O(1)).

    /** Insieme delle piante che possono crescere in Primavera. */
    private static final Set<Material> COLTURE_PRIMAVERILI = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS
    );
    /** Insieme delle piante che possono crescere in Estate. */
    private static final Set<Material> COLTURE_ESTIVE = Set.of(
            Material.MELON_STEM, Material.PUMPKIN_STEM, Material.SUGAR_CANE, Material.COCOA
    );
    /** Insieme delle piante che possono crescere in Autunno. */
    private static final Set<Material> COLTURE_AUTUNNALI = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES
    );
    // Nota: L'Inverno non ha un set definito, quindi nessuna pianta è considerata "di stagione".

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

            // Genera un numero casuale tra 0.0 e 1.0. Se è maggiore della probabilità consentita,
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
            case PRIMAVERA -> COLTURE_PRIMAVERILI.contains(cropType);
            case ESTATE -> COLTURE_ESTIVE.contains(cropType);
            case AUTUNNO -> COLTURE_AUTUNNALI.contains(cropType);
            // Per le stagioni non elencate (es. INVERNO), restituisce sempre false.
            default -> false;
        };
    }
}