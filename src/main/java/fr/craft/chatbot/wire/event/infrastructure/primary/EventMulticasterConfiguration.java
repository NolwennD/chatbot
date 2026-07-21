package fr.craft.chatbot.wire.event.infrastructure.primary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;

@Configuration
class EventMulticasterConfiguration {

  @Bean(name = "applicationEventMulticaster")
  ApplicationEventMulticaster applicationEventMulticaster() {
    var multicaster = new SimpleApplicationEventMulticaster();
    multicaster.setErrorHandler(new LoggingErrorHandler());
    return multicaster;
  }
}
