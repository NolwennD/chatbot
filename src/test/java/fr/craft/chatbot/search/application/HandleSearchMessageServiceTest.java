package fr.craft.chatbot.search.application;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.search.domain.PageLookup;
import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchOutcome;
import fr.craft.chatbot.search.domain.SearchQuery;
import fr.craft.chatbot.search.domain.SearchResponse;
import fr.craft.chatbot.search.domain.SearchResponseTranslator;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class HandleSearchMessageServiceTest {

  private static final SearchQuery QUERY = new SearchQuery("java");
  private static final PageSummary SUMMARY = new PageSummary(
    "Un langage de programmation.",
    "https://fr.wikipedia.org/wiki/Java_(langage)",
    false
  );

  @Mock
  private PageLookup pageLookup;

  @Mock
  private ChatMessagePublisher chatMessagePublisher;

  @Mock
  private SearchResponseTranslator translator;

  @Test
  void shouldStaySilentWhenTheMessageIsNotASearchCommand() {
    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("hello there"));

    verify(chatMessagePublisher, never()).send(anyString());
  }

  @Test
  void shouldSendEveryTranslatedResponseWhenFoundInTheBotLocale() {
    when(pageLookup.findSummary(QUERY, Locale.FRENCH)).thenReturn(Optional.of(SUMMARY));
    when(translator.translate(new SearchOutcome.Found(SUMMARY))).thenReturn(
      List.of(new SearchResponse("Un langage de programmation."), new SearchResponse("https://fr.wikipedia.org/wiki/Java_(langage)"))
    );

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("?wp java"));

    verify(chatMessagePublisher).send("Un langage de programmation.");
    verify(chatMessagePublisher).send("https://fr.wikipedia.org/wiki/Java_(langage)");
    verify(pageLookup, never()).findSummary(QUERY, Locale.ENGLISH);
  }

  @Test
  void shouldFallBackToEnglishWhenNotFoundInTheBotLocale() {
    var englishSummary = new PageSummary("A programming language.", "https://en.wikipedia.org/wiki/Java_(programming_language)", false);

    when(pageLookup.findSummary(QUERY, Locale.FRENCH)).thenReturn(Optional.empty());
    when(pageLookup.findSummary(QUERY, Locale.ENGLISH)).thenReturn(Optional.of(englishSummary));
    when(translator.translate(new SearchOutcome.Found(englishSummary))).thenReturn(List.of(new SearchResponse("A programming language.")));

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("?wp java"));

    verify(chatMessagePublisher).send("A programming language.");
  }

  @Test
  void shouldNotFallBackToEnglishWhenTheBotLocaleIsAlreadyEnglish() {
    when(pageLookup.findSummary(QUERY, Locale.ENGLISH)).thenReturn(Optional.empty());
    when(translator.translate(new SearchOutcome.NotFound(QUERY))).thenReturn(List.of(new SearchResponse("No article found.")));

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.ENGLISH).handle(new ChatMessage("?wp java"));

    verify(pageLookup, times(1)).findSummary(any(), any());
  }

  @Test
  void shouldNotFallBackToEnglishWhenTheResultIsAmbiguous() {
    var ambiguousSummary = new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true);

    when(pageLookup.findSummary(QUERY, Locale.FRENCH)).thenReturn(Optional.of(ambiguousSummary));
    when(translator.translate(new SearchOutcome.Ambiguous(QUERY, ambiguousSummary))).thenReturn(
      List.of(new SearchResponse("Pas de résumé, voici la page d'homonymie : https://fr.wikipedia.org/wiki/Java"))
    );

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("?wp java"));

    verify(pageLookup, never()).findSummary(QUERY, Locale.ENGLISH);
  }

  @Test
  void shouldSendTheNotFoundResponseWhenNothingIsFoundInEitherLocale() {
    when(pageLookup.findSummary(QUERY, Locale.FRENCH)).thenReturn(Optional.empty());
    when(pageLookup.findSummary(QUERY, Locale.ENGLISH)).thenReturn(Optional.empty());
    when(translator.translate(new SearchOutcome.NotFound(QUERY))).thenReturn(List.of(new SearchResponse("Aucun article trouvé.")));

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("?wp java"));

    verify(chatMessagePublisher).send("Aucun article trouvé.");
  }
}
