package it.cdl.calendario;

// --- IMPORTAZIONI MODIFICATE ---
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig; // <-- Importazione Chiave
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
// --- FINE IMPORTAZIONI ---

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

/**
 * Gestisce il caricamento e la fornitura of stringhe of testo traducibili.
 */
public class LanguageManager {

    private final CalendarioPlugin plugin;
    private FileConfiguration langConfig;
    private String missingTranslationMessage;

    public LanguageManager(CalendarioPlugin plugin) {
        this.plugin = plugin;
        loadLanguageFile();
    }

    private void loadLanguageFile() {
        String lang = plugin.getConfig().getString("language", "en_US");
        File langFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file '" + lang + ".yml' not found. Defaulting to 'en_US.yml'.");
            langFile = new File(plugin.getDataFolder(), "lang/en_US.yml");
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
        try (InputStream defaultConfigStream = plugin.getResource("lang/en_US.yml")) {
            if (defaultConfigStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
                langConfig.setDefaults(defaultConfig);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load default language file from JAR.", e);
        }

        this.missingTranslationMessage = langConfig.getString("errors.missing-translation", "&cMissing translation for: {key}");
    }

    /**
     * Ottiene una stringa tradotta e formattata dalla chiave specificata.
     * I codici colore legacy (es. &a) vengono convertiti.
     * @param key La chiave del messaggio (es. "Commands.reload-success").
     * @return La stringa tradotta e colorata (formato §).
     */
    public String getString(String key) {
        String message = langConfig.getString(key, this.missingTranslationMessage.replace("{key}", key));

        // Converte da '&' a '§'
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    /**
     * Ottiene una stringa tradotta, formattata e con i segnaposto sostituiti.
     * @param key La chiave del messaggio.
     * @param replacements Una serie of coppie "segnaposto, valore" (es. "{eventName}", "Luna di Sangue").
     * @return La stringa finale, pronta per essere inviata.
     */
    public String getString(String key, String... replacements) {

        // Ottieni il messaggio GREZZO con i codici &
        String rawMessage = langConfig.getString(key, this.missingTranslationMessage.replace("{key}", key));

        // Converti il messaggio grezzo in un Componente Adventure
        Component messageComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(rawMessage);

        // Esegui le sostituzioni usando l'API Adventure
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                String placeholder = replacements[i]; // es. "{eventName}"
                String rawValue = replacements[i + 1] != null ? replacements[i + 1] : ""; // es. "&e&lEgg Hunt"


                Component valueComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(rawValue);

                TextReplacementConfig replacementConfig = TextReplacementConfig.builder()
                        .matchLiteral(placeholder)
                        .replacement(valueComponent)
                        .build();

                // Esegui la sostituzione
                messageComponent = messageComponent.replaceText(replacementConfig);
            }
        }

        return LegacyComponentSerializer.legacySection().serialize(messageComponent);
    }
}