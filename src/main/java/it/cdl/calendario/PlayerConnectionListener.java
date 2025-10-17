package it.cdl.calendario;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gestisce gli eventi legati alla connessione e disconnessione dei giocatori.
 * Le sue responsabilità principali sono l'inizializzazione e la pulizia
 * dei sistemi specifici per ogni giocatore, come la BossBar e i Resource Pack.
 * È implementata come un "record" Java per una sintassi più compatta.
 */
public record PlayerConnectionListener(CalendarioPlugin plugin) implements Listener {

    /**
     * Metodo chiamato da Bukkit ogni volta che un giocatore entra nel server.
     * @param event L'evento di join del giocatore.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 1. Aggiunge il giocatore al gestore della BossBar, in modo che inizi a vederla.
        plugin.getBossBarManager().addPlayer(event.getPlayer());

        // 2. Invia al giocatore il Resource Pack specifico per la stagione corrente.
        TimeManager.Stagione currentSeason = plugin.getTimeManager().getEnumStagioneCorrente();
        String seasonName = currentSeason.name().toLowerCase();

        // Legge l'URL e l'hash SHA-1 del Resource Pack dal file di configurazione.
        String url = plugin.getConfig().getString("resource-packs." + seasonName + ".url", "");
        String sha1 = plugin.getConfig().getString("resource-packs." + seasonName + ".sha1", "");

        // Procede solo se sia l'URL che l'hash sono stati definiti nel config.
        if (!url.isEmpty() && !sha1.isEmpty()) {
            // Invia il pacchetto con un leggero ritardo (2 secondi).
            // Questo ritardo previene possibili problemi di caricamento che possono verificarsi
            // se il pacchetto viene inviato immediatamente al momento del login.
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> event.getPlayer().setResourcePack(url, sha1),
                    40L); // 40 tick = 2 secondi
        }
    }

    /**
     * Metodo chiamato da Bukkit ogni volta che un giocatore esce dal server.
     * @param event L'evento di quit del giocatore.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Rimuove il giocatore dal gestore della BossBar.
        // Questa operazione è fondamentale per evitare memory leak,
        // mantenendo in memoria riferimenti a giocatori non più online.
        plugin.getBossBarManager().removePlayer(event.getPlayer());
    }
}