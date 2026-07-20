package fr.craft.chatbot.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.IntegrationTest;
import fr.craft.chatbot.command.domain.ChatMessage;
import fr.craft.chatbot.command.infrastructure.TwitchChatFacade;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@IntegrationTest(properties = "chatbot.commands.file=src/test/resources/command/commands.txt")
@Import(TwitchChatCommandIT.RecordingFacadeConfiguration.class)
class TwitchChatCommandIT {

  private static final Path REPLY_FILE = Path.of("target/integration-test-output/chatbot-reply.txt");

  @Autowired
  private RecordingTwitchChatFacade twitchChatFacade;

  @BeforeEach
  @AfterEach
  void deleteReplyFile() throws IOException {
    Files.deleteIfExists(REPLY_FILE);
  }

  @Test
  void shouldWriteTheKnownCommandContentToTheOutputFileWhenReceivedFromChat() {
    twitchChatFacade.receiveMessage(new ChatMessage("!projet"));

    assertThat(REPLY_FILE).hasContent("Un chatbot Twitch qui répond aux commandes du chat");
  }

  @Test
  void shouldListKnownCommandsWhenTheCommandIsUnknown() {
    twitchChatFacade.receiveMessage(new ChatMessage("!doesnotexist"));

    assertThat(REPLY_FILE).hasContent("Commande inconnue. Commandes disponibles : !discord, !projet");
  }

  @Test
  void shouldListKnownCommandsWhenTheCommandsCommandIsReceived() {
    twitchChatFacade.receiveMessage(new ChatMessage("!commands"));

    assertThat(REPLY_FILE).hasContent("!discord, !projet");
  }

  @Test
  void shouldNotWriteAnOutputFileWhenTheMessageIsNotACommand() {
    twitchChatFacade.receiveMessage(new ChatMessage("hello there"));

    assertThat(REPLY_FILE).doesNotExist();
  }

  @Nested
  @IntegrationTest(properties = "chatbot.commands.file=src/test/resources/command/empty-commands.txt")
  @Import(TwitchChatCommandIT.RecordingFacadeConfiguration.class)
  class NoCommandsAvailable {

    @Autowired
    private RecordingTwitchChatFacade twitchChatFacade;

    @Test
    void shouldSayThereAreNoCommandsWhenNoneAreConfigured() {
      twitchChatFacade.receiveMessage(new ChatMessage("!anything"));

      assertThat(REPLY_FILE).hasContent("Aucune commande n'est disponible pour le moment.");
    }
  }

  @SuppressWarnings("NullAway.Init")
  static class RecordingTwitchChatFacade implements TwitchChatFacade {

    private Consumer<ChatMessage> listener;

    void receiveMessage(ChatMessage message) {
      listener.accept(message);
    }

    @Override
    public void onChatMessage(Consumer<ChatMessage> listener) {
      this.listener = listener;
    }

    @Override
    public void sendMessage(String message) {
      try {
        Files.createDirectories(REPLY_FILE.getParent());
        Files.writeString(REPLY_FILE, message);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @TestConfiguration
  static class RecordingFacadeConfiguration {

    @Bean
    @Primary
    RecordingTwitchChatFacade recordingTwitchChatFacade() {
      return new RecordingTwitchChatFacade();
    }
  }
}
