package fr.craft.chatbot.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import java.util.Locale;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@UnitTest
class SupportedLocaleTest {

  @Test
  void shouldKeepExplicitFrenchRegardlessOfSystemLocale() {
    Assertions.assertThat(SupportedLocale.resolve("fr", Locale.GERMANY)).isEqualTo(new SupportedLocale(Locale.FRENCH));
  }

  @Test
  void shouldKeepExplicitEnglishRegardlessOfSystemLocale() {
    assertThat(SupportedLocale.resolve("en", Locale.FRANCE)).isEqualTo(new SupportedLocale(Locale.ENGLISH));
  }

  @Test
  void shouldFallBackToEnglishWhenTheExplicitLocaleIsNotSupported() {
    assertThat(SupportedLocale.resolve("de", Locale.FRANCE)).isEqualTo(new SupportedLocale(Locale.ENGLISH));
  }

  @Test
  void shouldFollowTheSystemLocaleWhenNoneIsConfigured() {
    assertThat(SupportedLocale.resolve("", Locale.FRANCE)).isEqualTo(new SupportedLocale(Locale.FRENCH));
  }

  @Test
  void shouldTreatANullConfiguredValueAsNotConfigured() {
    assertThat(SupportedLocale.resolve(null, Locale.UK)).isEqualTo(new SupportedLocale(Locale.ENGLISH));
  }

  @Test
  void shouldFallBackToEnglishWhenNeitherTheConfiguredNorTheSystemLocaleIsSupported() {
    assertThat(SupportedLocale.resolve(null, Locale.GERMANY)).isEqualTo(new SupportedLocale(Locale.ENGLISH));
  }
}
