package fr.craft.chatbot.wire.event.infrastructure.primary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ErrorHandler;

class LoggingErrorHandler implements ErrorHandler {

  private static final Logger log = LoggerFactory.getLogger(LoggingErrorHandler.class);

  @Override
  public void handleError(Throwable throwable) {
    log.error("Error while handling an application event", throwable);
  }
}
