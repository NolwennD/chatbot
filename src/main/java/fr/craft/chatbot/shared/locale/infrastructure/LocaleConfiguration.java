package fr.craft.chatbot.shared.locale.infrastructure;

import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LocaleConfiguration {

  private static final Set<String> SUPPORTED_LANGUAGES = Set.of("fr", "en");
  private static final Locale FALLBACK = Locale.ENGLISH;

  @Bean
  Locale chatbotLocale(@Value("${chatbot.locale:}") String configuredLanguage) {
    return resolve(configuredLanguage, Locale.getDefault());
  }

  static Locale resolve(@Nullable String configuredLanguage, Locale systemDefault) {
    var language = configuredLanguage == null || configuredLanguage.isBlank() ? systemDefault.getLanguage() : configuredLanguage;

    return SUPPORTED_LANGUAGES.contains(language) ? Locale.forLanguageTag(language) : FALLBACK;
  }
}
