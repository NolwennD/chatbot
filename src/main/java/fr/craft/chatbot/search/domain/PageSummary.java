package fr.craft.chatbot.search.domain;

import fr.craft.chatbot.shared.error.domain.Assert;

public record PageSummary(String extract, String url, boolean disambiguation) {
  private static final int MAX_EXTRACT_LENGTH = 200;

  public PageSummary {
    Assert.field("extract", extract).notNull();
    Assert.field("url", url).notNull();
    extract = truncate(extract);
  }

  private static String truncate(String text) {
    if (text.length() <= MAX_EXTRACT_LENGTH) {
      return text;
    }

    var cut = text.substring(0, MAX_EXTRACT_LENGTH);
    var lastSpace = cut.lastIndexOf(' ');

    // ponytail: naive word-boundary truncation, no sentence-aware trimming — good enough for a chat summary
    return (lastSpace > 0 ? cut.substring(0, lastSpace) : cut) + "…";
  }
}
