package it.cdl.calendario;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gestisce l'esecuzione e il completamento tramite Tab del comando /calendario.
 * Include sottocomandi per la gestione della data, il reload del plugin e la
 * manipolazione del sistema di eventi.
 */
public record CalendarCommand(CalendarioPlugin plugin) implements CommandExecutor, TabCompleter {

    private static final List<String> MAIN_COMMANDS = Arrays.asList("set", "reload", "evento");
    private static final List<String> SET_COMMANDS = Arrays.asList("giorno", "mese", "anno");
    private static final List<String> EVENT_COMMANDS = Arrays.asList("start", "end", "status");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cDevi essere un operatore per usare questo comando.");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String mainArg = args[0].toLowerCase();

        switch (mainArg) {
            case "reload" -> handleReload(sender);
            case "set" -> handleSet(sender, args);
            case "evento" -> handleEvent(sender, args);
            default -> sendHelpMessage(sender);
        }
        return true;
    }

    /**
     * Gestisce la logica per il sottocomando "/calendario reload".
     * @param sender L'esecutore del comando.
     */
    private void handleReload(CommandSender sender) {
        sender.sendMessage("§eRicaricamento di CalendarioPlugin in corso...");
        TimeManager oldTimeManager = plugin.getTimeManager();
        int currentDay = oldTimeManager.getGiornoCorrente();
        int currentMonth = oldTimeManager.getMeseCorrente();
        int currentYear = oldTimeManager.getAnnoCorrente();
        plugin.shutdownPluginSystems();
        plugin.reloadConfig();
        plugin.startupPluginSystems();
        TimeManager newTimeManager = plugin.getTimeManager();
        newTimeManager.setGiornoCorrente(currentDay);
        newTimeManager.setMeseCorrente(currentMonth);
        newTimeManager.setAnnoCorrente(currentYear);
        sender.sendMessage("§aCalendarioPlugin ricaricato con successo! Le modifiche sono state applicate.");
    }

    /**
     * Gestisce la logica per il sottocomando "/calendario set", validando l'immissione.
     * @param sender L'esecutore del comando.
     * @param args Gli argomenti del comando.
     */
    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendHelpMessage(sender);
            return;
        }

        String tipo = args[1].toLowerCase();
        int valore;
        try {
            valore = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cIl valore deve essere un numero.");
            return;
        }

        TimeManager tm = plugin.getTimeManager();
        switch (tipo) {
            case "giorno" -> {
                if (valore < 1 || valore > tm.getGiorniNelMese(tm.getMeseCorrente())) {
                    sender.sendMessage("§cValore per il giorno non valido. Per il mese corrente, deve essere tra 1 e " + tm.getGiorniNelMese(tm.getMeseCorrente()) + ".");
                    return;
                }
                tm.setGiornoCorrente(valore);
            }
            case "mese" -> {
                if (valore < 1 || valore > 12) {
                    sender.sendMessage("§cValore per il mese non valido. Deve essere tra 1 e 12.");
                    return;
                }
                tm.setMeseCorrente(valore);
            }
            case "anno" -> {
                if (valore < 1) {
                    sender.sendMessage("§cValore per l'anno non valido. Deve essere 1 o superiore.");
                    return;
                }
                tm.setAnnoCorrente(valore);
            }
            default -> {
                sendHelpMessage(sender);
                return;
            }
        }

        plugin.getMainTaskInstance().forceUpdate();
        plugin.getEventManager().handleDateChange();
        sender.sendMessage("§aData aggiornata. Tutti i sistemi stagionali sono stati ricalibrati.");
    }

    /**
     * Gestisce la logica per il sottocomando "/calendario evento".
     * @param sender L'esecutore del comando.
     * @param args Gli argomenti del comando.
     */
    private void handleEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelpMessage(sender);
            return;
        }

        String subCommand = args[1].toLowerCase();
        EventManager em = plugin.getEventManager();

        switch (subCommand) {
            case "start" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /calendario evento start <id_evento>");
                    return;
                }
                if (em.forceStartEvent(args[2])) {
                    sender.sendMessage("§aEvento '" + args[2] + "' avviato forzatamente.");
                } else {
                    sender.sendMessage("§cEvento '" + args[2] + "' non trovato in events.yml.");
                }
            }
            case "end" -> {
                if (em.forceEndActiveEvent()) {
                    sender.sendMessage("§eEvento attivo terminato forzatamente.");
                } else {
                    sender.sendMessage("§cNon c'è nessun evento attivo da terminare.");
                }
            }
            case "status" -> {
                CustomEvent active = em.getActiveEvent();
                if (active != null) {
                    sender.sendMessage("§aEvento attivo: " + active.displayName().replace('&', '§'));
                } else {
                    sender.sendMessage("§eNessun evento attivo al momento.");
                }
            }
            default -> sendHelpMessage(sender);
        }
    }

    /**
     * Invia un messaggio di aiuto formattato al mittente del comando.
     * @param sender L'entità a cui inviare il messaggio.
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6--- Comandi CalendarioPlugin ---");
        sender.sendMessage("§e/calendario set <giorno|mese|anno> <valore> §7- Imposta la data.");
        sender.sendMessage("§e/calendario reload §7- Ricarica la configurazione del plugin.");
        sender.sendMessage("§e/calendario evento <start|end|status> [id] §7- Gestisce gli eventi.");
    }

    /**
     * Metodo che gestisce i suggerimenti automatici quando l'utente preme il tasto Tab.
     * @return Una lista di suggerimenti appropriati al contesto.
     */
    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp()) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return MAIN_COMMANDS.stream()
                    .filter(cmd -> cmd.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String mainArg = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            List<String> sourceList = null;
            if (mainArg.equals("set")) {
                sourceList = SET_COMMANDS;
            } else if (mainArg.equals("evento")) {
                sourceList = EVENT_COMMANDS;
            }

            if (sourceList != null) {
                return sourceList.stream()
                        .filter(cmd -> cmd.startsWith(input))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}