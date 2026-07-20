package fr.craft.chatbot.shared.locale.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import java.util.Locale;
import org.junit.jupiter.api.Test;

@UnitTest
class LocaleConfigurationTest {

  @Test
  void shouldKeepExplicitFrenchRegardlessOfSystemLocale() {
    assertThat(LocaleConfiguration.resolve("fr", Locale.GERMANY)).isEqualTo(Locale.FRENCH);
  }

  @Test
  void shouldKeepExplicitEnglishRegardlessOfSystemLocale() {
    assertThat(LocaleConfiguration.resolve("en", Locale.FRANCE)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void shouldFallBackToEnglishWhenTheExplicitLocaleIsNotSupported() {
    assertThat(LocaleConfiguration.resolve("de", Locale.FRANCE)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void shouldFollowTheSystemLocaleWhenNoneIsConfigured() {
    assertThat(LocaleConfiguration.resolve("", Locale.FRANCE)).isEqualTo(Locale.FRENCH);
  }

  @Test
  void shouldTreatANullConfiguredValueAsNotConfigured() {
    assertThat(LocaleConfiguration.resolve(null, Locale.UK)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void shouldFallBackToEnglishWhenNeitherTheConfiguredNorTheSystemLocaleIsSupported() {
    assertThat(LocaleConfiguration.resolve(null, Locale.GERMANY)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void shouldExposeTheResolvedLocaleAsASpringBean() {
    assertThat(new LocaleConfiguration().chatbotLocale("fr")).isEqualTo(Locale.FRENCH);
  }
}
