# Chatbot

The application connects to a Twitch channel's chat and replies to commands (e.g. `!about`).

## Prerequisites

### Java

You need to have Java 25:

- [JDK 25](https://openjdk.java.net/projects/jdk/25/)

### Twitch Credentials

The bot needs a Twitch account and an OAuth token with chat scopes (`chat:read`, `chat:edit`). Generate one for
that account using a tool such as the [Twitch Token Generator](https://twitchtokengenerator.com/) or the
[Twitch CLI](https://dev.twitch.tv/docs/cli/), then strip the `oauth:` prefix if the tool includes it.

These values are never stored in the repository — export them as environment variables before starting the
application.

### Commands

Commands and their responses are defined in [commands.txt](commands.txt), one per line, formatted as `!bug=bugs are undocumented features`.
Lines that are blank or start with `#` are ignored. The file is re-read on every command, so you can add or edit commands
while the application is running — no restart needed.

### Node.js and PNPM (only for contributing)

Before you can build this project, you must install and configure the following dependencies on your machine:

[Node.js](https://nodejs.org/): We use Node to run a development web server and build the project.
Depending on your system, you can install Node either from source or as a pre-packaged bundle.

After installing Node, you should be able to run the following command to install development tools.
You will only need to run this command when dependencies change in [package.json](package.json).

```
pnpm install
```

<!-- seed4j-needle-localEnvironment -->

## Settings

### Commands file

By default, the application reads `commands.txt` in the current directory. To point it at a different file, set the
`CHATBOT_COMMANDS_FILE` environment variable, or pass it as a command-line argument when starting the application:

### Linux/Unix

```bash
export TWITCH_CHANNEL=your_channel_name
export TWITCH_BOT_USERNAME=your_bot_account_name
export TWITCH_OAUTH_TOKEN=your_oauth_token #without oauth:
#export CHATBOT_LOCALE=en # auto-detected from the system by default
#export CHATBOT_COMMANDS_FILE=/path/to/commands.txt # defaut is command.txt in this directory
./mvnw
```

Alternatively, run [setup-env.sh](setup-env.sh): it prompts for any of the variables above (and `CHATBOT_COMMANDS_FILE`
`CHATBOT_LOCALE`) that aren't already set in your environment, then starts the application. If you typed any value,
it then offers to save them to your shell profile (`~/.bashrc` or `~/.zshrc`) so they persist across sessions —
declining still starts the application with the values you just entered.

```bash
./setup-env.sh
```

Pass `-u`/`--update` to review and override every variable, including ones already set:

```bash
./setup-env.sh --update
```

Pass `-c`/`--clear` to remove any saved block from your shell profile and unset every variable in your current
shell. It doesn't prompt for anything or start the application. Unsetting only affects the shell that ran the
command, so it must be applied with `eval`:

```bash
eval "$(./setup-env.sh --clear)"
```

### Windows

`setup-env.sh` is a POSIX shell script — it won't run directly in `cmd.exe` or PowerShell.

- **Git Bash or WSL** (recommended): both provide the POSIX tools the script needs (`sh`, `sed`, `grep`), so
  `./setup-env.sh` works exactly as documented above. Git Bash ships with
  [Git for Windows](https://git-scm.com/download/win); WSL is built into Windows 10/11.
- **Native PowerShell**: set the variables by hand, then run `mvnw.cmd`:

  ```powershell
  $env:TWITCH_CHANNEL = "your_channel_name"
  $env:TWITCH_BOT_USERNAME = "your_bot_account_name"
  $env:TWITCH_OAUTH_TOKEN = "your_oauth_token"
  .\mvnw.cmd
  ```

  `$env:` only lasts for the current PowerShell window. To persist a variable across sessions instead, use `setx`
  (takes effect in _new_ windows, not the current one):

  ```powershell
  setx TWITCH_CHANNEL "your_channel_name"
  ```

### Language

The bot's own messages (unknown command, no command available) are translated. By default the language follows the
system locale: if it's French it's used as-is, otherwise (English, German, anything else) the bot falls back to
English. Set `CHATBOT_LOCALE` to override this and pick a language explicitly, regardless of the system locale:

```bash
export CHATBOT_LOCALE=en
./mvnw
```

An unsupported `CHATBOT_LOCALE` value also falls back to English rather than failing to start.

On Windows, Git Bash and WSL use the `export` form above unchanged. In native PowerShell:

```powershell
$env:CHATBOT_LOCALE = "en"
.\mvnw.cmd
```

Supported languages: `fr` and `en` (fallback). Translations live in
[messages.properties](src/main/resources/messages.properties) (default, French) and
[messages_en.properties](src/main/resources/messages_en.properties). To add another language, add a
`messages_<locale>.properties` file with the same keys next to those two.

This only affects the bot's own messages — the responses configured in [commands.txt](commands.txt) are plain text
and always sent as written, regardless of `CHATBOT_LOCALE`.

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
