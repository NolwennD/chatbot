package fr.craft.chatbot.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.IntegrationTest;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
import fr.craft.chatbot.shared.twitch.infrastructure.primary.TwitchChatReceiver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
@Import(TwitchChatCommandIT.RecordingConfiguration.class)
class TwitchChatCommandIT {

  private static final Path REPLY_FILE = Path.of("target/integration-test-output/chatbot-reply.txt");

  @Autowired
  private FakeTwitchChatReceiver twitchChatReceiver;

  @BeforeEach
  @AfterEach
  void deleteReplyFile() throws IOException {
    Files.deleteIfExists(REPLY_FILE);
  }

  @Test
  void shouldWriteTheKnownCommandContentToTheOutputFileWhenReceivedFromChat() {
    twitchChatReceiver.receiveMessage(new ChatMessage("!projet"));

    assertThat(REPLY_FILE).hasContent("Un chatbot Twitch qui répond aux commandes du chat");
  }

  @Test
  void shouldListKnownCommandsWhenTheCommandIsUnknown() {
    twitchChatReceiver.receiveMessage(new ChatMessage("!doesnotexist"));

    assertThat(REPLY_FILE).hasContent("Commande inconnue. Commandes disponibles : !discord, !projet");
  }

  @Test
  void shouldListKnownCommandsWhenTheCommandsCommandIsReceived() {
    twitchChatReceiver.receiveMessage(new ChatMessage("!commands"));

    assertThat(REPLY_FILE).hasContent("!discord, !projet");
  }

  @Test
  void shouldNotWriteAnOutputFileWhenTheMessageIsNotACommand() {
    twitchChatReceiver.receiveMessage(new ChatMessage("hello there"));

    assertThat(REPLY_FILE).doesNotExist();
  }

  @Nested
  @IntegrationTest(properties = "chatbot.commands.file=src/test/resources/command/empty-commands.txt")
  @Import(TwitchChatCommandIT.RecordingConfiguration.class)
  class NoCommandsAvailable {

    @Autowired
    private FakeTwitchChatReceiver twitchChatReceiver;

    @Test
    void shouldSayThereAreNoCommandsWhenNoneAreConfigured() {
      twitchChatReceiver.receiveMessage(new ChatMessage("!anything"));

      assertThat(REPLY_FILE).hasContent("Aucune commande n'est disponible pour le moment.");
    }
  }

  static class FakeTwitchChatReceiver implements TwitchChatReceiver {

    private final List<Consumer<ChatMessage>> listeners = new ArrayList<>();

    void receiveMessage(ChatMessage message) {
      listeners.forEach(listener -> listener.accept(message));
    }

    @Override
    public void onChatMessage(Consumer<ChatMessage> listener) {
      listeners.add(listener);
    }
  }

  static class RecordingChatMessagePublisher implements ChatMessagePublisher {

    @Override
    public void send(String message) {
      try {
        Files.createDirectories(REPLY_FILE.getParent());
        Files.writeString(REPLY_FILE, message);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @TestConfiguration
  static class RecordingConfiguration {

    @Bean
    @Primary
    FakeTwitchChatReceiver fakeTwitchChatReceiver() {
      return new FakeTwitchChatReceiver();
    }

    @Bean
    @Primary
    RecordingChatMessagePublisher recordingChatMessagePublisher() {
      return new RecordingChatMessagePublisher();
    }
  }
}
