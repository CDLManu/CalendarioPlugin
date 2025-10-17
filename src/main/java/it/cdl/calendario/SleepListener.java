package it.cdl.calendario;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;

/**
 * Gestisce l'interazione tra il ciclo del tempo personalizzato del plugin
 * e la meccanica del sonno di Minecraft. La sua strategia consiste nel cedere e
 * riprendere temporaneamente il controllo della gamerule 'doDaylightCycle' per
 * permettere a Minecraft di eseguire la sua animazione nativa di avanzamento del tempo.
 */
public final class SleepListener implements Listener {

    private final CalendarioPlugin plugin;

    /**
     * La percentuale di giocatori richiesta per saltare la notte,
     * letta una sola volta dal config e memorizzata per efficienza.
     */
    private final int sleepingPercentageNeeded;

    /**
     * Costruttore del listener.
     * Inizializza il riferimento al plugin e mette in cache la percentuale
     * di giocatori che devono dormire, leggendola dal file di configurazione.
     * @param plugin L'istanza principale di CalendarioPlugin.
     */
    public SleepListener(CalendarioPlugin plugin) {
        this.plugin = plugin;
        this.sleepingPercentageNeeded = plugin.getConfig().getInt("sleep-mechanics.players-sleeping-percentage", 100);
    }

    /**
     * Metodo chiamato quando un giocatore entra in un letto.
     * Se la percentuale di giocatori a letto raggiunge la soglia configurata,
     * restituisce temporaneamente il controllo del tempo a Minecraft per avviare il salto notturno.
     * @param event L'evento di entrata nel letto.
     */
    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }

        // Esegue il controllo con un ritardo di 2 tick per garantire che lo stato di sonno
        // di tutti i giocatori sia stato aggiornato correttamente dal server.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            World world = event.getPlayer().getWorld();

            long sleepingPlayers = world.getPlayers().stream().filter(Player::isSleeping).count();
            long totalPlayers = world.getPlayers().stream().filter(p -> !p.isSleepingIgnored()).count();
            if (totalPlayers == 0) return;

            double currentSleepingPercentage = ((double) sleepingPlayers / totalPlayers) * 100.0;

            // Confronta la percentuale attuale con il valore memorizzato in cache.
            if (currentSleepingPercentage >= this.sleepingPercentageNeeded) {
                plugin.getLogger().info("[Calendario] Percentuale di giocatori che dormono (" + (int)currentSleepingPercentage + "%) raggiunta. Cedo il controllo a Minecraft.");
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            }
        }, 2L);
    }

    /**
     * Metodo chiamato quando un giocatore si alza da un letto.
     * Se il risveglio avviene al mattino (indicando un sonno riuscito),
     * il plugin riprende il controllo del ciclo del tempo.
     * @param event L'evento di uscita dal letto.
     */
    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        World world = event.getPlayer().getWorld();
        long currentTime = world.getTime();

        // Controlla se è mattina presto (tick 0-1000), segno di un risveglio dopo un sonno completato.
        if (currentTime >= 0 && currentTime < 1000) {
            plugin.getLogger().info("[Calendario] Il giocatore si è svegliato. Riprendo il controllo del tempo.");
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

            // Notifica il CalendarTask di sincronizzarsi con il nuovo stato del tempo.
            CalendarTask mainTask = plugin.getMainTaskInstance();
            if (mainTask != null) {
                mainTask.acceptTimeSkip();
            }
        }
    }
}