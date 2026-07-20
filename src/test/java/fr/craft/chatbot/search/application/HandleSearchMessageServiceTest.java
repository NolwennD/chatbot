package fr.craft.chatbot.search.application;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.search.domain.PageLookup;
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

  @Mock
  private PageLookup pageLookup;

  @Mock
  private ChatMessagePublisher chatMessagePublisher;

  @Mock
  private SearchResponseTranslator translator;

  @Test
  void shouldNotFallBackToEnglishWhenTheBotLocaleIsAlreadyEnglish() {
    when(pageLookup.findSummary(QUERY, Locale.ENGLISH)).thenReturn(Optional.empty());
    when(translator.translate(new SearchOutcome.NotFound(QUERY, Locale.ENGLISH))).thenReturn(
      List.of(new SearchResponse("No article found."))
    );

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.ENGLISH).handle(new ChatMessage("?wp java"));

    verify(pageLookup, times(1)).findSummary(any(), any());
  }

  @Test
  void shouldSendTheNotFoundResponseWhenNothingIsFoundInEitherLocale() {
    when(pageLookup.findSummary(QUERY, Locale.FRENCH)).thenReturn(Optional.empty());
    when(pageLookup.findSummary(QUERY, Locale.ENGLISH)).thenReturn(Optional.empty());
    when(translator.translate(new SearchOutcome.NotFound(QUERY, Locale.ENGLISH))).thenReturn(
      List.of(new SearchResponse("Aucun article trouvé."))
    );

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("?wp java"));

    verify(chatMessagePublisher).send("Aucun article trouvé.");
  }
}
