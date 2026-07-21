package fr.craft.chatbot.wire.event.infrastructure.primary;

import ch.qos.logback.classic.Level;
import fr.craft.chatbot.Logs;
import fr.craft.chatbot.LogsSpy;
import fr.craft.chatbot.LogsSpyExtension;
import fr.craft.chatbot.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@UnitTest
@ExtendWith(LogsSpyExtension.class)
class LoggingErrorHandlerTest {

  @Logs
  private LogsSpy logs;

  @Test
  void shouldLogTheErrorAtErrorLevel() {
    new LoggingErrorHandler().handleError(new RuntimeException("boom"));

    logs.shouldHave(Level.ERROR, "Error while handling an application event");
  }
}
