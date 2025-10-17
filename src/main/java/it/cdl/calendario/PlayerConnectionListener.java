package it.cdl.calendario;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gestisce gli eventi legati alla connessione e disconnessione dei giocatori.
 * Le sue responsabilità principali sono l'inizializzazione e la pulizia
 * dei sistemi specifici per ogni giocatore, come la BossBar e i Resource Pack stagionali.
 * È implementata come "record" Java per una sintassi più compatta e immutabile.
 */
public record PlayerConnectionListener(CalendarioPlugin plugin) implements Listener {

    /**
     * Metodo chiamato da Bukkit ogni volta che un giocatore entra nel server.
     * Si occupa di integrare il giocatore nei sistemi dinamici del plugin.
     * <p>
     * Azioni eseguite:
     * <ul>
     * <li>Aggiunge il giocatore al {@link BossBarManager} per visualizzare la barra delle informazioni.</li>
     * <li>Invia al giocatore il resource pack stagionale corretto con un breve ritardo,
     * per garantire che il client sia pronto a riceverlo.</li>
     * </ul>
     *
     * @param event L'evento di join del giocatore, fornito da Bukkit.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getBossBarManager().addPlayer(event.getPlayer());

        TimeManager.Stagione currentSeason = plugin.getTimeManager().getEnumStagioneCorrente();

        String seasonConfigKey = switch (currentSeason) {
            case INVERNO -> "inverno";
            case PRIMAVERA -> "primavera";
            case ESTATE -> "estate";
            case AUTUNNO -> "autunno";
        };

        String url = plugin.getConfig().getString("resource-packs." + seasonConfigKey + ".url", "");
        String sha1 = plugin.getConfig().getString("resource-packs." + seasonConfigKey + ".sha1", "");

        if (!url.isEmpty() && !sha1.isEmpty()) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> event.getPlayer().setResourcePack(url, sha1),
                    40L);
        }
    }

    /**
     * Metodo chiamato da Bukkit ogni volta che un giocatore esce dal server.
     * Esegue le operazioni di pulizia necessarie per il giocatore che si disconnette.
     * <p>
     * Azioni eseguite:
     * <ul>
     * <li>Rimuove il giocatore dal {@link BossBarManager}. Questa operazione è fondamentale
     * per prevenire memory leak, evitando di mantenere in memoria riferimenti a
     * giocatori non più online.</li>
     * </ul>
     *
     * @param event L'evento di quit del giocatore, fornito da Bukkit.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getBossBarManager().removePlayer(event.getPlayer());
    }
}