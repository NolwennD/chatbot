package fr.craft.chatbot.search.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchQuery;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@UnitTest
class RestWikipediaPageLookupTest {

  private final RestClient.Builder restClientBuilder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
  private final RestWikipediaPageLookup pageLookup = new RestWikipediaPageLookup(restClientBuilder);

  @Test
  void shouldReturnASummaryWhenThePageExists() {
    server
      .expect(requestTo("https://fr.wikipedia.org/api/rest_v1/page/summary/java"))
      .andRespond(
        withSuccess(
          """
          {"type":"standard","extract":"Un langage de programmation.","content_urls":{"desktop":{"page":"https://fr.wikipedia.org/wiki/Java_(langage)"}}}
          """,
          MediaType.APPLICATION_JSON
        )
      );

    var summary = pageLookup.findSummary(new SearchQuery("java"), Locale.FRENCH);

    assertThat(summary).contains(new PageSummary("Un langage de programmation.", "https://fr.wikipedia.org/wiki/Java_(langage)", false));
  }

  @Test
  void shouldFlagDisambiguationPages() {
    server
      .expect(requestTo("https://fr.wikipedia.org/api/rest_v1/page/summary/java"))
      .andRespond(
        withSuccess(
          """
          {"type":"disambiguation","extract":"","content_urls":{"desktop":{"page":"https://fr.wikipedia.org/wiki/Java"}}}
          """,
          MediaType.APPLICATION_JSON
        )
      );

    var summary = pageLookup.findSummary(new SearchQuery("java"), Locale.FRENCH);

    assertThat(summary).contains(new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true));
  }

  @Test
  void shouldReturnEmptyWhenThePageDoesNotExist() {
    server.expect(requestTo("https://fr.wikipedia.org/api/rest_v1/page/summary/doesnotexist")).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(pageLookup.findSummary(new SearchQuery("doesnotexist"), Locale.FRENCH)).isEmpty();
  }

  @Test
  void shouldReturnEmptyOnServerError() {
    server.expect(requestTo("https://en.wikipedia.org/api/rest_v1/page/summary/java")).andRespond(withServerError());

    assertThat(pageLookup.findSummary(new SearchQuery("java"), Locale.ENGLISH)).isEmpty();
  }

  @Test
  void shouldEncodeMultiWordQueries() {
    server
      .expect(requestTo("https://fr.wikipedia.org/api/rest_v1/page/summary/coq%20de%20java"))
      .andRespond(
        withSuccess(
          """
          {"type":"standard","extract":"Une race de poule.","content_urls":{"desktop":{"page":"https://fr.wikipedia.org/wiki/Coq_de_Java"}}}
          """,
          MediaType.APPLICATION_JSON
        )
      );

    var summary = pageLookup.findSummary(new SearchQuery("coq de java"), Locale.FRENCH);

    assertThat(summary).contains(new PageSummary("Une race de poule.", "https://fr.wikipedia.org/wiki/Coq_de_Java", false));
  }
}
