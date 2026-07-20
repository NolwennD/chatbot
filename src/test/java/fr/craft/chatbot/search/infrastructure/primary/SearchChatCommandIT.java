package fr.craft.chatbot.search.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.IntegrationTest;
import fr.craft.chatbot.search.domain.PageLookup;
import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchQuery;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@IntegrationTest(properties = "chatbot.locale=fr")
@Import(SearchChatCommandIT.SearchTestConfiguration.class)
class SearchChatCommandIT {

  private static final Path REPLY_FILE = Path.of("target/integration-test-output/search-chatbot-reply.txt");

  @Autowired
  private RecordingTwitchChatFacade twitchChatFacade;

  @BeforeEach
  @AfterEach
  void deleteReplyFile() throws IOException {
    Files.deleteIfExists(REPLY_FILE);
  }

  @Test
  void shouldNotWriteAnythingWhenTheMessageIsNotASearchCommand() {
    twitchChatFacade.receiveMessage(new ChatMessage("wiki Idris"));

    assertThat(REPLY_FILE).doesNotExist();
  }

  @Test
  void shouldWriteTheDisambiguationMessageWhenTheResultIsAmbiguous() {
    twitchChatFacade.receiveMessage(new ChatMessage("?wp java"));

    assertThat(REPLY_FILE).hasContent(
      """
      Pas de résumé pour "java", voici la page d'homonymie : https://fr.wikipedia.org/wiki/Java"""
    );
  }

  @Test
  void shouldWriteTheExtractThenTheLinkWhenFoundDirectlyInTheBotLocale() {
    twitchChatFacade.receiveMessage(new ChatMessage("?wiki coq de java"));

    assertThat(REPLY_FILE).hasContent(
      """
      Une race de poule.
      https://fr.wikipedia.org/wiki/Coq_de_Java"""
    );
  }

  @Test
  void shouldFallBackToEnglishAndWriteBothLinesWhenNotFoundInTheBotLocale() {
    twitchChatFacade.receiveMessage(new ChatMessage("?wiki onboarding"));

    assertThat(REPLY_FILE).hasContent(
      """
      Onboarding is the process of integrating a new employee.
      https://en.wikipedia.org/wiki/Onboarding"""
    );
  }

  static class RecordingTwitchChatFacade implements TwitchChatFacade {

    private final List<Consumer<ChatMessage>> listeners = new ArrayList<>();

    void receiveMessage(ChatMessage message) {
      listeners.forEach(listener -> listener.accept(message));
    }

    @Override
    public void onChatMessage(Consumer<ChatMessage> listener) {
      listeners.add(listener);
    }

    @Override
    public void sendMessage(String message) {
      try {
        Files.createDirectories(REPLY_FILE.getParent());
        var line = Files.exists(REPLY_FILE) ? "\n" + message : message;
        Files.writeString(REPLY_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  static class FakePageLookup implements PageLookup {

    @Override
    public Optional<PageSummary> findSummary(SearchQuery query, Locale locale) {
      return switch (query.value()) {
        case "java" -> locale.equals(Locale.FRENCH)
          ? Optional.of(new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true))
          : Optional.empty();
        case "coq de java" -> locale.equals(Locale.FRENCH)
          ? Optional.of(new PageSummary("Une race de poule.", "https://fr.wikipedia.org/wiki/Coq_de_Java", false))
          : Optional.empty();
        case "onboarding" -> locale.equals(Locale.ENGLISH)
          ? Optional.of(
              new PageSummary("Onboarding is the process of integrating a new employee.", "https://en.wikipedia.org/wiki/Onboarding", false)
            )
          : Optional.empty();
        default -> Optional.empty();
      };
    }
  }

  @TestConfiguration
  static class SearchTestConfiguration {

    @Bean
    @Primary
    RecordingTwitchChatFacade recordingTwitchChatFacade() {
      return new RecordingTwitchChatFacade();
    }

    @Bean
    @Primary
    FakePageLookup fakePageLookup() {
      return new FakePageLookup();
    }
  }
}
