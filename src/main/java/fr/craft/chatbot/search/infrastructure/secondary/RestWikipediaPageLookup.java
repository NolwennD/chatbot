package fr.craft.chatbot.search.infrastructure.secondary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.craft.chatbot.search.domain.PageLookup;
import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchQuery;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Repository
class RestWikipediaPageLookup implements PageLookup {

  private static final String SUMMARY_URI = "https://{language}.wikipedia.org/api/rest_v1/page/summary/{title}";

  private final RestClient restClient;

  RestWikipediaPageLookup(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  @Override
  public Optional<PageSummary> findSummary(SearchQuery query, Locale locale) {
    try {
      var response = restClient.get().uri(SUMMARY_URI, locale.getLanguage(), query.value()).retrieve().body(WikipediaSummaryResponse.class);

      return Optional.ofNullable(response).map(this::toPageSummary);
    } catch (RestClientException e) {
      // ponytail: any HTTP/network failure (404, 5xx, timeout) is treated as "not found" —
      // no distinction, no retry. Revisit if silent Wikipedia outages become a real problem.
      return Optional.empty();
    }
  }

  private PageSummary toPageSummary(WikipediaSummaryResponse response) {
    return new PageSummary(response.extract(), response.contentUrls().desktop().page(), "disambiguation".equals(response.type()));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record WikipediaSummaryResponse(String type, String extract, @JsonProperty("content_urls") ContentUrls contentUrls) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentUrls(Desktop desktop) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Desktop(String page) {}
  }
}
