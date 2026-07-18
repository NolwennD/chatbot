package fr.craft.chatbot.command.infrastructure.secondary;

import java.util.Locale;
import java.util.Set;

record SupportedLocale(Locale value) {
  private static final Set<String> SUPPORTED_LANGUAGES = Set.of("fr", "en");
  private static final Locale FALLBACK = Locale.ENGLISH;

  static SupportedLocale resolve(String configuredLanguage, Locale systemDefault) {
    var language = configuredLanguage == null || configuredLanguage.isBlank() ? systemDefault.getLanguage() : configuredLanguage;

    return SUPPORTED_LANGUAGES.contains(language) ? new SupportedLocale(Locale.forLanguageTag(language)) : new SupportedLocale(FALLBACK);
  }
}
