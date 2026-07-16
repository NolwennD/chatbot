package fr.craft.chatbot.cucumber;

import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import fr.craft.chatbot.ChatbotApp;
import fr.craft.chatbot.cucumber.CucumberConfiguration.CucumberRestClientConfiguration;
import fr.craft.chatbot.cucumber.rest.CucumberRestClient;
import fr.craft.chatbot.cucumber.rest.CucumberRestTestContext;

@ActiveProfiles("test")
@CucumberContextConfiguration
@AutoConfigureRestTestClient
@SpringBootTest(
  classes = { ChatbotApp.class, CucumberRestClientConfiguration.class },
  webEnvironment = WebEnvironment.RANDOM_PORT
)
public class CucumberConfiguration {

  private final CucumberRestClient rest;

  CucumberConfiguration(CucumberRestClient rest) {
    this.rest = rest;
  }

  @Before
  public void resetTestContext() {
    CucumberRestTestContext.reset();
  }

  @Before
  public void setupRestClient() {
    rest.setupRestClient();
  }

  @TestConfiguration
  static class CucumberRestClientConfiguration {

    @Bean
    CucumberRestClient cucumberRestClient(RestTestClient rest) {
      return new CucumberRestClient(rest);
    }
  }
}
