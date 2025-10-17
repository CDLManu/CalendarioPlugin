package it.cdl.calendario;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gestisce l'applicazione di effetti visivi e ambientali legati alle stagioni.
 * Questa classe agisce come Listener per eventi specifici (es. Piazzamento di blocchi)
 * e orchestra task asincroni per modificare l'ambiente, come la formazione di neve
 * in inverno o lo scioglimento in primavera.
 */
public class SeasonalEffectsManager implements Listener {

    private final CalendarioPlugin plugin;
    private final Random random = new Random();
    /**
     * Riferimento al task Bukkit attualmente attivo per gli effetti stagionali.
     * Viene mantenuto per poterlo annullare al cambio di stagione.
     */
    private BukkitTask activeEffectTask = null;
    /**
     * Insieme di materiali il cui piazzamento viene tracciato in modalità debug.
     */
    private static final Set<Material> LOGGED_BLOCKS = Set.of(
            Material.ICE, Material.SNOW, Material.SNOW_BLOCK, Material.POWDER_SNOW
    );

    /**
     * Insieme di biomi considerati "temperati", letto dal config.yml,
     * dove gli effetti di gelo e disgelo stagionale verranno applicati.
     */
    private final Set<Biome> mildBiomes;

    /**
     * Lista di fiori che possono nascere in primavera, letta dal config.yml.
     */
    private final List<Material> spawnableFlowers;


    /**
     * Costruttore del manager degli effetti stagionali.
     * Carica le impostazioni (biomi, fiori) dal config.yml.
     * @param plugin L'istanza principale del plugin.
     */
    public SeasonalEffectsManager(CalendarioPlugin plugin) {
        this.plugin = plugin;

        // Carica i biomi temperati dal config
        this.mildBiomes = loadBiomesFromConfig();

        // Carica i fiori primaverili dal config
        this.spawnableFlowers = loadMaterialsFromConfig();
        if (spawnableFlowers.isEmpty()) {
            // Fallback se la lista è vuota o non valida, per evitare errori
            spawnableFlowers.add(Material.POPPY);
            spawnableFlowers.add(Material.DANDELION);
        }
    }

    /**
     * Metodo helper per caricare una lista di Biomi dal config.
     */
    private Set<Biome> loadBiomesFromConfig() {
        List<String> biomeNames = plugin.getConfig().getStringList("visual-effects.mild-biomes");
        Set<Biome> biomes = EnumSet.noneOf(Biome.class);

        for (String name : biomeNames) {
            try {
                biomes.add(Biome.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(
                        plugin.getLanguageManager().getString("logs.invalid-config-biome", "{biome}", name)
                );
            }
        }
        return biomes;
    }

    /**
     * Metodo helper per caricare una lista di Materiali dal config.
     */
    private List<Material> loadMaterialsFromConfig() {
        List<String> materialNames = plugin.getConfig().getStringList("visual-effects.primavera.spawnable-flowers");
        List<Material> materials = new ArrayList<>();

        for (String name : materialNames) {
            Material mat = Material.matchMaterial(name.toUpperCase());
            if (mat != null && mat.isItem()) { // Assicura che sia un blocco/fiore piazzabile
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
     * Intercetta il piazzamento di blocchi da parte dei giocatori.
     * Se la modalità debug è attiva, registra nella console il piazzamento
     * di blocchi "freddi" (neve, ghiaccio).
     *
     * @param event L'evento di piazzamento del blocco.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.isDebugMode()) {
            Block block = event.getBlockPlaced();
            if (LOGGED_BLOCKS.contains(block.getType())) {
                Player player = event.getPlayer();
                Location loc = block.getLocation();
                plugin.getLogger().info(plugin.getLanguageManager().getString("seasonal-effects.debug-log-player",
                        "{playerName}", player.getName(),
                        "{blockType}", block.getType().name(),
                        "{worldName}", loc.getWorld().getName(),

                        "{x}", String.format("%.0f", loc.getX()),
                        "{y}", String.format("%.0f", loc.getY()),
                        "{z}", String.format("%.0f", loc.getZ())
                ));
            }
        }
    }

    /**
     * Metodo orchestratore chiamato al cambio di stagione.
     * Interrompe gli effetti precedenti, invia il nuovo resource pack
     * e avvia i nuovi effetti ambientali corrispondenti.
     *
     * @param newSeason La nuova stagione da applicare.
     */
    public void handleSeasonChange(TimeManager.Stagione newSeason) {
        stopAllEffects();
        sendResourcePack(newSeason);
        LanguageManager lang = plugin.getLanguageManager();

        // --- CORREZIONE: Convertito switch da espressione a istruzione ---
        // La variabile 'seasonConfigKey' non era necessaria in questo contesto.
        // Lo switch ora esegue solo le azioni necessarie per ogni stagione.
        switch (newSeason) {
            case INVERNO -> {
                plugin.getLogger().info(lang.getString("seasonal-effects.winter-arrival"));
                startWinterEffects();
            }
            case PRIMAVERA -> {
                plugin.getLogger().info(lang.getString("seasonal-effects.spring-arrival"));
                startSpringEffects();
            }
            case AUTUNNO -> {
                plugin.getLogger().info(lang.getString("seasonal-effects.autumn-arrival"));
                startAutumnEffects();
            }
            // Il caso ESTATE e default non richiedono azioni specifiche qui.
        }
    }

    /**
     * Invia a tutti i giocatori online il resource pack stagionale definito nel config.yml.
     * Se il pack per la stagione corrente non è definito, tenta di usare quello della primavera
     * come fallback.
     *
     * @param season La stagione per cui inviare il resource pack.
     */
    public void sendResourcePack(TimeManager.Stagione season) {
        String seasonConfigKey = switch (season) {
            case INVERNO -> "inverno";
            case ESTATE -> "estate";
            case AUTUNNO -> "autunno";
            default -> "primavera";
        };
        String url = plugin.getConfig().getString("resource-packs." + seasonConfigKey + ".url", "");
        String sha1 = plugin.getConfig().getString("resource-packs." + seasonConfigKey + ".sha1", "");
        if (url.isEmpty() || sha1.isEmpty()) {
            plugin.getLogger().info("Nessun resource pack trovato per " + seasonConfigKey + ", usando il default (Primavera)...");
            url = plugin.getConfig().getString("resource-packs.primavera.url", "");
            sha1 = plugin.getConfig().getString("resource-packs.primavera.sha1", "");
        }

        if (url.isEmpty() || sha1.isEmpty()) {
            plugin.getLogger().warning("Nessun resource pack di default (Primavera) è impostato. Nessun pack verrà inviato.");
            return;
        }

        plugin.getLogger().info("Invio del resource pack per la stagione " + season.name() + " a tutti i giocatori...");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setResourcePack(url, sha1);
        }
    }

    /**
     * Annulla in modo sicuro il task degli effetti stagionali correntemente in esecuzione.
     * Previene la sovrapposizione di effetti al cambio di stagione.
     */
    public void stopAllEffects() {
        if (activeEffectTask != null && !activeEffectTask.isCancelled()) {
            activeEffectTask.cancel();
            activeEffectTask = null;
        }
    }

    /**
     * Avvia un task periodico che applica un effetto a un chunk casuale vicino a un giocatore.
     * L'operazione di ricerca del blocco viene eseguita in modo asincrono per non impattare
     * le performance del server.
     *
     * @param period      L'intervallo in tick tra ogni esecuzione dell'effetto.
     * @param chunkEffect L'azione (effetto) da applicare al chunk selezionato.
     */
    private void startSeasonalEffectTask(long period, Consumer<Chunk> chunkEffect) {
        stopAllEffects();
        activeEffectTask = new BukkitRunnable() {
            @Override
            public void run() {
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (players.isEmpty()) return;

                Player randomPlayer = players.get(random.nextInt(players.size()));
                Chunk chunk = randomPlayer.getLocation().getChunk();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    for (int i = 0; i < 5; i++) {
                        chunkEffect.accept(chunk);
                    }
                });
            }
        }.runTaskTimer(plugin, 40L, period);
    }

    /**
     * Avvia il task per gli effetti invernali (formazione di neve e ghiaccio).
     */
    private void startWinterEffects() {
        startSeasonalEffectTask(100L, this::applyWinterToChunk);
    }

    /**
     * Avvia il task per gli effetti primaverili (scioglimento di neve e ghiaccio).
     */
    private void startSpringEffects() {
        startSeasonalEffectTask(80L, this::applySpringToChunk);
    }

    /**
     * Avvia il task per gli effetti autunnali (attualmente nessuno).
     */
    private void startAutumnEffects() {
        // Attualmente non sono previste azioni periodiche per l'autunno.
    }

    /**
     * Applica la logica di congelamento a un blocco casuale all'interno di un chunk.
     * Trasforma l'acqua in ghiaccio o deposita neve sui blocchi solidi.
     * La probabilità aumenta se ci sono già blocchi freddi nelle vicinanze.
     *
     * @param chunk Il chunk su cui applicare l'effetto.
     */
    private void applyWinterToChunk(Chunk chunk) {
        int x = chunk.getX() * 16 + random.nextInt(16);
        int z = chunk.getZ() * 16 + random.nextInt(16);
        World world = chunk.getWorld();
        Block highestBlock = world.getHighestBlockAt(x, z);

        // MODIFICA: Controlla contro la lista caricata dal config
        if (!this.mildBiomes.contains(highestBlock.getBiome())) return;

        int baseChance = plugin.getConfig().getInt("visual-effects.inverno.freeze-chance", 30);
        int spreadBonusPerBlock = 15;
        int surroundingIce = 0;
        Block blockBelow = highestBlock.getRelative(BlockFace.DOWN);

        if (isColdBlock(blockBelow.getRelative(BlockFace.NORTH).getType())) surroundingIce++;
        if (isColdBlock(blockBelow.getRelative(BlockFace.SOUTH).getType())) surroundingIce++;
        if (isColdBlock(blockBelow.getRelative(BlockFace.EAST).getType())) surroundingIce++;
        if (isColdBlock(blockBelow.getRelative(BlockFace.WEST).getType())) surroundingIce++;

        int finalChance = baseChance + (surroundingIce * spreadBonusPerBlock);
        if (random.nextInt(100) < finalChance) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Material blockType = highestBlock.getType();
                if (blockType == Material.WATER) {
                    highestBlock.setType(Material.ICE);
                    logNaturalChange("seasonal-effects.actions.frozen", highestBlock.getLocation());

                } else if (blockType == Material.AIR && blockBelow.getType().isSolid() && blockBelow.getType() != Material.ICE) {
                    highestBlock.setType(Material.SNOW);
                    logNaturalChange("seasonal-effects.actions.snow-formed", highestBlock.getLocation());
                }
            });
        }
    }

    /**
     * Applica la logica di scioglimento a un blocco casuale all'interno di un chunk.
     * Rimuove la neve o trasforma il ghiaccio in acqua. Può anche far nascere fiori.
     *
     * @param chunk Il chunk su cui applicare l'effetto.
     */
    private void applySpringToChunk(Chunk chunk) {
        int thawChance = plugin.getConfig().getInt("visual-effects.primavera.thaw-chance", 35);
        if (random.nextInt(100) < thawChance) {
            int x = chunk.getX() * 16 + random.nextInt(16);
            int z = chunk.getZ() * 16 + random.nextInt(16);
            Block highestBlock = chunk.getWorld().getHighestBlockAt(x, z);
            Material blockType = highestBlock.getType();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (blockType == Material.SNOW) {
                    highestBlock.setType(Material.AIR);
                    logNaturalChange("seasonal-effects.actions.snow-melted", highestBlock.getLocation());
                    int flowerChance = plugin.getConfig().getInt("visual-effects.primavera.flower-spawn-chance", 5);

                    // MODIFICA: Sceglie un fiore casuale dalla lista caricata dal config
                    if (highestBlock.getRelative(BlockFace.DOWN).getType() == Material.GRASS_BLOCK &&
                            random.nextInt(100) < flowerChance &&
                            !spawnableFlowers.isEmpty()) {

                        Material flowerToSpawn = spawnableFlowers.get(random.nextInt(spawnableFlowers.size()));
                        highestBlock.setType(flowerToSpawn);
                    }
                } else if (blockType == Material.ICE) {

                    highestBlock.setType(Material.WATER);
                    logNaturalChange("seasonal-effects.actions.ice-melted", highestBlock.getLocation());
                }
            });
        }
    }

    /**
     * Metodo di utilità per verificare se un materiale è un "blocco freddo".
     *
     * @param material Il materiale da controllare.
     * @return true se il materiale è ghiaccio o neve, altrimenti false.
     */
    private boolean isColdBlock(Material material) {
        return material == Material.ICE ||
                material == Material.SNOW || material == Material.SNOW_BLOCK;
    }

    /**
     * Registra un messaggio di debug per una modifica ambientale avvenuta naturalmente
     * a causa degli effetti stagionali del plugin.
     *
     * @param actionKey La chiave di traduzione per l'azione eseguita (es. "Congelato").
     * @param location  La posizione in cui è avvenuta la modifica.
     */
    private void logNaturalChange(String actionKey, Location location) {
        if (plugin.isDebugMode()) {
            LanguageManager lang = plugin.getLanguageManager();
            String action = lang.getString(actionKey);

            plugin.getLogger().info(lang.getString("seasonal-effects.debug-log-natural",
                    "{action}", action,
                    "{worldName}", location.getWorld().getName(),
                    "{x}", String.format("%.0f", location.getX()),
                    "{y}", String.format("%.0f", location.getY()),

                    "{z}", String.format("%.0f", location.getZ())
            ));
        }
    }
}