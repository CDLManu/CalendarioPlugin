package it.cdl.calendario;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestisce tutti i comandi relativi al plugin Calendario.
 * Implementa sia CommandExecutor che TabCompleter per la logica e l'auto completamento.
 */
public class CalendarCommand implements CommandExecutor, TabCompleter {

    private final CalendarioPlugin plugin;
    private final LanguageManager lang;

    public CalendarCommand(CalendarioPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            // Mostra il messaggio di aiuto se non vengono forniti argomenti
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "help" -> sendHelpMessage(sender);
            case "reload" -> handleReload(sender);
            case "set" -> handleSet(sender, args);
            case "event" -> handleEvent(sender, args); // Passa sender e args
            default -> {
                // Messaggio di comando non valido
                sender.sendMessage(lang.getString("commands.invalid-subcommand"));
                return true;
            }
        }
        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(lang.getString("commands.help-header"));
        sender.sendMessage(lang.getString("commands.help-set"));
        sender.sendMessage(lang.getString("commands.help-reload"));
        sender.sendMessage(lang.getString("commands.help-event"));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.isOp()) {
            sender.sendMessage(lang.getString("commands.no-permission"));
            return;
        }
        plugin.reload();
        sender.sendMessage(lang.getString("commands.reload-success"));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(lang.getString("commands.no-permission"));
            return;
        }
        if (args.length < 3) {
            // Messaggio di uso corretto
            sender.sendMessage(lang.getString("commands.set-usage"));
            return;
        }

        String type = args[1].toLowerCase();
        int value;
        try {
            value = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(lang.getString("commands.invalid-value-number"));
            return;
        }

        TimeManager tm = plugin.getTimeManager();
        switch (type) {
            case "giorno" -> {
                if (value < 1 || value > tm.getGiorniNelMese()) {
                    sender.sendMessage(lang.getString("commands.invalid-value-day", "{maxDays}", String.valueOf(tm.getGiorniNelMese())));
                    return;
                }
                tm.setGiorno(value);
            }
            case "mese" -> {
                if (value < 1 || value > 12) {
                    sender.sendMessage(lang.getString("commands.invalid-value-month"));
                    return;
                }
                tm.setMese(value);
            }
            case "anno" -> {
                if (value < 1) {
                    sender.sendMessage(lang.getString("commands.invalid-value-year"));
                    return;
                }
                tm.setAnno(value);
            }
            default -> {
                // Messaggio di uso corretto
                sender.sendMessage(lang.getString("commands.set-usage"));
                return;
            }
        }

        plugin.getEventManager().handleDateChange();
        plugin.getMainTaskInstance().forceUpdate();
        sender.sendMessage(lang.getString("commands.date-updated"));
    }

    private void handleEvent(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(lang.getString("commands.no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(lang.getString("commands.event-usage"));
            return;
        }

        String action = args[1].toLowerCase();
        EventManager em = plugin.getEventManager();

        switch (action) {
            case "start" -> {
                if (args.length < 3) {
                    sender.sendMessage(lang.getString("commands.event-start-usage"));
                    return;
                }
                String eventIdToStart = args[2].toLowerCase();
                CustomEvent event = em.getEventById(eventIdToStart); // Metodo da aggiungere a EventManager

                if (event == null) {
                    sender.sendMessage(lang.getString("commands.event-not-found", "{eventName}", eventIdToStart));
                    return;
                }

                // Termina l'evento corrente prima di avviarne uno nuovo
                if (em.getActiveEvent() != null) { // Metodo da aggiungere
                    em.endActiveEvent();
                }

                em.startEvent(event); // Metodo da rendere public
                sender.sendMessage(lang.getString("commands.event-started", "{eventName}", event.displayName()));
            }
            case "end" -> {
                if (em.getActiveEvent() == null) { // Metodo da aggiungere
                    sender.sendMessage(lang.getString("commands.event-none-active"));
                    return;
                }
                em.endActiveEvent();
                sender.sendMessage(lang.getString("commands.event-ended"));
            }
            case "status" -> {
                CustomEvent activeEvent = em.getActiveEvent(); // Metodo da aggiungere
                if (activeEvent == null) {
                    sender.sendMessage(lang.getString("commands.event-status-none"));
                } else {
                    sender.sendMessage(lang.getString("commands.event-status-active", "{eventName}", activeEvent.displayName()));
                }
            }
            default -> sender.sendMessage(lang.getString("commands.event-usage"));
        }
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp()) return null;

        if (args.length == 1) {
            // Aggiunto "help"
            return List.of("set", "reload", "event", "help");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return List.of("giorno", "mese", "anno");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("event")) {
            // Aggiunti i sottocomandi di "event"
            return List.of("start", "end", "status");
        }

        return new ArrayList<>();
    }
}