package fr.craft.chatbot.command.infrastructure;

@SuppressWarnings("NullAway.Init")
public class TwitchProperties {

  private String channel;
  private String botUsername;
  private String oauthToken;

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getBotUsername() {
    return botUsername;
  }

  public void setBotUsername(String botUsername) {
    this.botUsername = botUsername;
  }

  public String getOauthToken() {
    return oauthToken;
  }

  public void setOauthToken(String oauthToken) {
    this.oauthToken = oauthToken;
  }
}
