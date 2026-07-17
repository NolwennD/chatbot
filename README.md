# Chatbot

## Prerequisites

### Java

You need to have Java 25:

- [JDK 25](https://openjdk.java.net/projects/jdk/25/)

### Node.js and NPM

Before you can build this project, you must install and configure the following dependencies on your machine:

[Node.js](https://nodejs.org/): We use Node to run a development web server and build the project.
Depending on your system, you can install Node either from source or as a pre-packaged bundle.

After installing Node, you should be able to run the following command to install development tools.
You will only need to run this command when dependencies change in [package.json](package.json).

```
pnpm install
```

## Local environment

- [Local server](http://localhost:8080)

<!-- seed4j-needle-localEnvironment -->

## Twitch chatbot

The application connects to a Twitch channel's chat and replies to commands (e.g. `!projet`). Commands and their
responses are defined in [commands.txt](commands.txt), one per line, formatted as `!commande=réponse`. Lines that
are blank or start with `#` are ignored. The file is re-read on every command, so you can add or edit commands
while the application is running — no restart needed.

### Credentials

The bot needs a Twitch account and an OAuth token with chat scopes (`chat:read`, `chat:edit`). Generate one for
that account using a tool such as the [Twitch Token Generator](https://twitchtokengenerator.com/) or the
[Twitch CLI](https://dev.twitch.tv/docs/cli/), then strip the `oauth:` prefix if the tool includes it.

These values are never stored in the repository — export them as environment variables before starting the
application:

```bash
export TWITCH_CHANNEL=your_channel_name
export TWITCH_BOT_USERNAME=your_bot_account_name
export TWITCH_OAUTH_TOKEN=your_oauth_token
./mvnw
```

## Start up

```bash
./mvnw
```

<!-- seed4j-needle-startupCommand -->

## Documentation

- [Hexagonal architecture](documentation/hexagonal-architecture.md)
- [Package types](documentation/package-types.md)
- [Assertions](documentation/assertions.md)
- [Logs Spy](documentation/logs-spy.md)
- [CORS configuration](documentation/cors-configuration.md)
- [Cucumber](documentation/cucumber.md)

<!-- seed4j-needle-documentation -->

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
