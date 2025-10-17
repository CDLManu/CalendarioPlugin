package it.cdl.calendario;

import java.util.List;
import java.util.Set;

/**
 * Record che rappresenta un singolo evento personalizzato caricato da events.yml.
 * Essendo immutabile, è un modo sicuro per contenere i dati di un evento.
 */
public record CustomEvent(
        String id,
        String displayName,
        String type,
        String triggerDate,
        int chance,
        Set<String> seasons,
        int durationDays,
        List<String> startCommands,
        List<String> endCommands
) {}