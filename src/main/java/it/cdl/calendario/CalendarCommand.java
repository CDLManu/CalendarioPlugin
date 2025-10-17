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
            // TODO: Mostra un messaggio di aiuto
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "set" -> handleSet(sender, args);
            case "event" -> handleEvent();
            default -> {
                // TODO: Messaggio di comando non valido
                return true;
            }
        }
        return true;
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
            // TODO: Messaggio di uso corretto /calendario set <giorno|mese|anno> <valore>
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
                // TODO: Messaggio di uso corretto
                return;
            }
        }

        plugin.getEventManager().handleDateChange();
        plugin.getMainTaskInstance().forceUpdate();
        sender.sendMessage(lang.getString("commands.date-updated"));
    }

    private void handleEvent() {
        // Implementazione della logica per gestire gli eventi
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp()) return null;

        if (args.length == 1) {
            return List.of("set", "reload", "event");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return List.of("giorno", "mese", "anno");
        }

        return new ArrayList<>();
    }
}