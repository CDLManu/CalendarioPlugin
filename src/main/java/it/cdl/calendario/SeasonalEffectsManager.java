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
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gestisce tutti gli effetti visivi legati alle stagioni e il logging per specifici blocchi.
 * Agisce come Listener per catturare i piazzamenti di blocchi da parte dei giocatori e utilizza
 * task schedulati per applicare cambiamenti ambientali come la formazione di neve e lo scioglimento.
 */
public class SeasonalEffectsManager implements Listener {

    private final CalendarioPlugin plugin;
    /** Un'unica istanza di Random per migliorare le performance. */
    private final Random random = new Random();
    /** Riferimento al task degli effetti stagionali attualmente attivo, per permetterne l'interruzione. */
    private BukkitTask activeEffectTask = null;

    /** Insieme di blocchi il cui piazzamento da parte dei giocatori verrà loggato se il debug è attivo. */
    private static final Set<Material> LOGGED_BLOCKS = Set.of(
            Material.ICE, Material.SNOW, Material.SNOW_BLOCK, Material.POWDER_SNOW
    );
    /** Insieme di biomi temperati dove gli effetti stagionali come neve e ghiaccio possono verificarsi. */
    private static final Set<Biome> MILD_BIOMES = Set.of(
            Biome.PLAINS, Biome.FOREST, Biome.BIRCH_FOREST, Biome.DARK_FOREST,
            Biome.TAIGA, Biome.MEADOW, Biome.SWAMP, Biome.RIVER, Biome.BEACH
    );

    public SeasonalEffectsManager(CalendarioPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ascolta gli eventi di piazzamento di blocchi per loggare quando un giocatore piazza neve o ghiaccio.
     * Questo log avviene solo se 'debug-mode' è abilitato nel file config.yml.
     * @param event L'evento di piazzamento del blocco.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Esegue il codice di debug solo se la modalità è attiva.
        if (plugin.isDebugMode()) {
            Block block = event.getBlockPlaced();
            if (LOGGED_BLOCKS.contains(block.getType())) {
                Player player = event.getPlayer();
                Location loc = block.getLocation();
                String worldName = loc.getWorld().getName();
                plugin.getLogger().info(String.format(
                        "[DEBUG] Il giocatore %s ha piazzato %s nel mondo %s alle coordinate X:%.0f Y:%.0f Z:%.0f",
                        player.getName(),
                        block.getType().name(),
                        worldName,
                        loc.getX(),
                        loc.getY(),
                        loc.getZ()
                ));
            }
        }
    }

    /**
     * Handler principale per i cambi di stagione. Ferma gli effetti esistenti,
     * invia il resource pack appropriato e avvia gli effetti della nuova stagione.
     * @param newSeason La stagione appena iniziata.
     */
    public void handleSeasonChange(TimeManager.Stagione newSeason) {
        stopAllEffects();
        sendResourcePack(newSeason);
        switch (newSeason) {
            case INVERNO -> startWinterEffects();
            case PRIMAVERA -> startSpringEffects();
            case AUTUNNO -> startAutumnEffects();
        }
    }

    /**
     * Invia a tutti i giocatori online il Resource Pack configurato per la stagione specificata.
     * @param season La stagione per cui inviare il Resource Pack.
     */
    public void sendResourcePack(TimeManager.Stagione season) {
        String seasonName = season.name().toLowerCase();
        String url = plugin.getConfig().getString("resource-packs." + seasonName + ".url", "");
        String sha1 = plugin.getConfig().getString("resource-packs." + seasonName + ".sha1", "");

        if (url.isEmpty() || sha1.isEmpty()) {
            plugin.getLogger().info("Nessuna pack trovata per " + seasonName + ", uso quella di default (Primavera)...");
            url = plugin.getConfig().getString("resource-packs.primavera.url", "");
            sha1 = plugin.getConfig().getString("resource-packs.primavera.sha1", "");
        }

        if (url.isEmpty() || sha1.isEmpty()) {
            plugin.getLogger().warning("Nessuna pack di default (Primavera) è impostata. Nessuna resource pack sarà inviata.");
            return;
        }

        plugin.getLogger().info("Invio della resource pack per la stagione " + season.name() + " a tutti i giocatori...");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setResourcePack(url, sha1);
        }
    }

    /**
     * Ferma in modo sicuro il task degli effetti visivi attualmente in esecuzione.
     */
    public void stopAllEffects() {
        if (activeEffectTask != null && !activeEffectTask.isCancelled()) {
            activeEffectTask.cancel();
            activeEffectTask = null;
        }
    }

    /**
     * Avvia un task periodico per applicare effetti visivi, eseguendo la logica pesante in modo asincrono.
     * @param period L'intervallo in tick tra ogni esecuzione.
     * @param chunkEffect L'azione specifica da eseguire su un chunk.
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

    private void startWinterEffects() {
        plugin.getLogger().info("È arrivato l'Inverno! Il mondo inizierà a ghiacciare...");
        startSeasonalEffectTask(100L, this::applyWinterToChunk);
    }

    private void startSpringEffects() {
        plugin.getLogger().info("È arrivata la Primavera! La neve si scioglie...");
        startSeasonalEffectTask(80L, this::applySpringToChunk);
    }

    private void startAutumnEffects() {
        plugin.getLogger().info("È arrivato l'Autunno! La resource pack stagionale è stata inviata ai giocatori.");
    }

    /**
     * Applica l'effetto di congelamento invernale in un punto casuale di un chunk,
     * con una probabilità aumentata se ci sono già blocchi di neve o ghiaccio nelle vicinanze.
     * @param chunk Il chunk in cui applicare l'effetto.
     */
    private void applyWinterToChunk(Chunk chunk) {
        int x = chunk.getX() * 16 + random.nextInt(16);
        int z = chunk.getZ() * 16 + random.nextInt(16);
        World world = chunk.getWorld();
        Block highestBlock = world.getHighestBlockAt(x, z);

        if (!MILD_BIOMES.contains(highestBlock.getBiome())) return;

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
                    logNaturalChange("Blocco congelato", highestBlock.getLocation());
                } else if (blockType == Material.AIR && blockBelow.getType().isSolid() && blockBelow.getType() != Material.ICE) {
                    highestBlock.setType(Material.SNOW);
                    logNaturalChange("Neve formata", highestBlock.getLocation());
                }
            });
        }
    }

    private boolean isColdBlock(Material material) {
        return material == Material.ICE || material == Material.SNOW || material == Material.SNOW_BLOCK;
    }

    /**
     * Applica l'effetto di scioglimento primaverile in un punto casuale di un chunk.
     * @param chunk Il chunk in cui applicare l'effetto.
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
                    logNaturalChange("Neve sciolta", highestBlock.getLocation());
                    int flowerChance = plugin.getConfig().getInt("visual-effects.primavera.flower-spawn-chance", 5);
                    if (highestBlock.getRelative(BlockFace.DOWN).getType() == Material.GRASS_BLOCK && random.nextInt(100) < flowerChance) {
                        highestBlock.setType(random.nextBoolean() ? Material.POPPY : Material.DANDELION);
                    }
                } else if (blockType == Material.ICE) {
                    highestBlock.setType(Material.WATER);
                    logNaturalChange("Ghiaccio sciolto", highestBlock.getLocation());
                }
            });
        }
    }

    /**
     * Metodo di utilità per loggare un cambiamento ambientale naturale nella console.
     * Il messaggio di log viene inviato solo se 'debug-mode' è attivo nel config.
     * @param action Descrizione del cambiamento (es. "Blocco congelato").
     * @param location La posizione del blocco modificato.
     */
    private void logNaturalChange(String action, Location location) {
        // Esegue il codice di debug solo se la modalità è attiva.
        if (plugin.isDebugMode()) {
            plugin.getLogger().info(String.format(
                    "[DEBUG] %s nel mondo %s alle coordinate X:%.0f Y:%.0f Z:%.0f",
                    action,
                    location.getWorld().getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ()
            ));
        }
    }
}