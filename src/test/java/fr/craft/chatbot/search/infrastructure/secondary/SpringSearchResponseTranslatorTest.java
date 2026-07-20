package fr.craft.chatbot.search.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchOutcome;
import fr.craft.chatbot.search.domain.SearchQuery;
import fr.craft.chatbot.search.domain.SearchResponse;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

@UnitTest
class SpringSearchResponseTranslatorTest {

  private final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

  {
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");
  }

  private final SpringSearchResponseTranslator translator = new SpringSearchResponseTranslator(messageSource, Locale.FRENCH);
  private final SpringSearchResponseTranslator englishTranslator = new SpringSearchResponseTranslator(messageSource, Locale.ENGLISH);

  @Test
  void shouldReturnTheSummaryThenTheLinkWhenFound() {
    var summary = new PageSummary("Un langage de programmation.", "https://fr.wikipedia.org/wiki/Java_(langage)", false);

    assertThat(translator.translate(new SearchOutcome.Found(summary))).containsExactly(
      new SearchResponse("Un langage de programmation."),
      new SearchResponse("https://fr.wikipedia.org/wiki/Java_(langage)")
    );
  }

  @Test
  void shouldExplainTheDisambiguationPageWhenAmbiguous() {
    var summary = new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true);

    assertThat(translator.translate(new SearchOutcome.Ambiguous(new SearchQuery("java"), summary))).containsExactly(
      new SearchResponse("Pas de résumé pour \"java\", voici la page d'homonymie : https://fr.wikipedia.org/wiki/Java")
    );
  }

  @Test
  void shouldSayNoArticleWasFoundWhenNotFound() {
    assertThat(translator.translate(new SearchOutcome.NotFound(new SearchQuery("doesnotexist"), Locale.FRENCH))).containsExactly(
      new SearchResponse("Aucun article trouvé pour \"doesnotexist\".")
    );
  }

  @Test
  void shouldSayNoArticleWasFoundInEnglishWhenNotFound() {
    assertThat(englishTranslator.translate(new SearchOutcome.NotFound(new SearchQuery("doesnotexist"), Locale.ENGLISH))).containsExactly(
      new SearchResponse("No article found for \"doesnotexist\".")
    );
  }
}
