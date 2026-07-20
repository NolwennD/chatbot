# `setup-env.sh --container` — run the chatbot via Docker or Podman

## Context and goal

`setup-env.sh` currently only prompts for the Twitch/chatbot environment
variables and then runs the app via `exec ./mvnw "$@"`. There is no
container setup anywhere in this repo (no `Dockerfile`, no compose file).

Add an opt-in `--container` flag that runs the app in a container instead
of via Maven, choosing between Docker and Podman — whichever is actually
installed and working, asking only when both are available. `./mvnw`
remains the default, documented way to run the app; nothing changes for
users who don't pass the new flag.

## New files

- **`Dockerfile`** (repo root) — multi-stage build:
  - `build` stage, `eclipse-temurin:25-jdk`: copies the repo, runs
    `./mvnw package -DskipTests` (the app is already built and verified by
    the time someone runs `setup-env.sh --container`; re-running tests
    inside the image build is redundant and slows down every container
    start).
  - runtime stage, `eclipse-temurin:25-jre`: `WORKDIR /app`, copies only
    the built jar from the `build` stage (`target/*.jar` → `app.jar`),
    `ENTRYPOINT ["java", "-jar", "app.jar"]`.
  - Self-contained: the host needs nothing but Docker or Podman itself —
    no local JDK/Maven required, unlike the default `./mvnw` path.
- **`.dockerignore`** (repo root) — excludes `target/`, `.git/`,
  `.claude/`, `node_modules/` from the build context.

## Changes to `setup-env.sh`

Everything before the final launch step is unchanged: `--container` and
`--container=docker|podman` only affect the launch step at the very end
(`exec ./mvnw "$@"` vs. `run_container`). The existing `ask`/`ask_secret`
calls that collect `TWITCH_CHANNEL`, `TWITCH_BOT_USERNAME`,
`TWITCH_OAUTH_TOKEN`, `CHATBOT_COMMANDS_FILE`, and `CHATBOT_LOCALE` still
run exactly as they do today, regardless of which launch mode is chosen.

### Flag parsing

Extend the existing `case "${1:-}"` block (alongside `-u|--update` and
`-c|--clear`) with:

- `--container` — enable container mode, auto-detect the tool.
- `--container=docker` / `--container=podman` — enable container mode,
  force that specific tool (skips detection entirely).

A new `container=""` variable holds the resolved tool name (`docker` or
`podman`), empty when container mode isn't requested.

### Detection (`--container` with no value)

New function `detect_container_tool`:

1. For each candidate tool, check both `command -v "$tool"` _and_
   `"$tool" info` succeeding — `--version` alone isn't enough, since a
   binary can be installed without its daemon/backend actually running
   (Docker Desktop not started, `podman.socket` inactive, etc.).
2. Exactly one candidate works → use it directly, no prompt.
3. Both work → prompt via the existing `ask`-style mechanism, storing the
   answer in `CHATBOT_CONTAINER_TOOL`. Like the other variables, this gets
   offered for saving to the shell profile via `offer_save`, so the
   question isn't repeated on the next run. `--update` re-prompts it like
   any other saved variable.
4. Neither works → print a clear error (`Neither docker nor podman is
available and working.`) to stderr and exit non-zero. No silent
   fallback to `./mvnw` — the user explicitly asked for container mode.

### Forced tool (`--container=docker` / `--container=podman`)

Skip detection. Verify the named tool passes the same
`command -v` + `info` check; if not, print a clear error naming the
requested tool and exit non-zero (same no-silent-fallback rule as above).

### Build and run

New function `run_container`, called at the very end instead of
`exec ./mvnw "$@"` when container mode is active:

```sh
"$container" build -t chatbot .
"$container" run --rm \
  -e TWITCH_CHANNEL -e TWITCH_BOT_USERNAME -e TWITCH_OAUTH_TOKEN -e CHATBOT_LOCALE \
  -v "$PWD/$CHATBOT_COMMANDS_FILE:/app/$CHATBOT_COMMANDS_FILE" \
  chatbot
```

- **No port mapping.** The bot's connection to Twitch chat
  (`TwitchClientConfiguration`/`Twitch4jChatFacade`, via Twitch4j's
  `TwitchChatBuilder`) is outbound-only — the container reaches out to
  Twitch's chat servers, nothing needs to reach into the container. Every
  container runtime allows outbound traffic by default, no `-p` needed.
- `-e VAR` (no `=value`) forwards the named variable's current value from
  the calling shell, already exported by the existing `ask`/`ask_secret`
  calls earlier in the script — no new plumbing needed there.
- The `commands.txt` volume mount uses the **same relative path** as
  source (resolved against the host's `$PWD`) and destination (resolved
  against the container's `WORKDIR /app`). This preserves the documented
  hot-reload behavior (CLAUDE.md: "The file is re-read on every command,
  so edits take effect without a restart") for the default value
  (`commands.txt`) and any custom _relative_ path, with no hardcoding.
  **Known limitation:** an absolute `CHATBOT_COMMANDS_FILE` path is not
  handled correctly by this mount (it would land at
  `/app/<absolute path>` inside the container) — out of scope for this
  iteration; `CHATBOT_COMMANDS_FILE` is documented/defaulted as a relative
  path today, so this isn't a regression from current behavior.
- Trailing positional arguments (`"$@"`, forwarded to `./mvnw` in the
  default path) are Maven-specific and don't apply to `$container run`;
  they're ignored in container mode. Noted with a comment in the script.

### Error handling

- Build or run failure (bad Dockerfile change, image build error, etc.):
  let the container tool's own error and exit code propagate
  unmodified — same philosophy as the existing `exec ./mvnw "$@"`, which
  doesn't interpret Maven failures either.
- `--clear`: add `CHATBOT_CONTAINER_TOOL` to the printed `unset` list.
  `strip_profile_block` already removes the whole saved block regardless
  of which variables it contains, so no change needed there.

### Help comment

Update the header comment block (lines 1–14) to document the new flag,
matching the existing style:

```
#   ./setup-env.sh --container                    # run via Docker or Podman (auto-detected)
#   ./setup-env.sh --container=docker|podman       # run via a specific container tool
```

## Testing

No shell test harness exists in this repo (no bats/shellcheck in CI) and
adding one for a single script would be disproportionate. Instead, this
feature ships with a manual verification checklist (documented in the
implementation PR description, not automated):

1. Only Docker installed and running → `--container` picks it with no
   prompt.
2. Only Podman installed and running → same, with Podman.
3. Both installed and running → `--container` prompts once, offers to
   save, second run doesn't prompt again.
4. Neither installed/running → `--container` fails with a clear message,
   non-zero exit, no Maven fallback.
5. `--container=docker` when only Podman is available → fails with a
   clear message naming Docker specifically.
6. Bot connects to the configured Twitch channel and responds to a `!`
   command in chat once the container is running — no port mapping needed
   for this to work (see the "No port mapping" note above).
7. Editing `commands.txt` on the host while the container is running is
   picked up by the next chat command, without restarting the container.
8. `--clear` unsets `CHATBOT_CONTAINER_TOOL` and removes it from the saved
   profile block.

## Out of scope for this iteration

- Docker/Podman Compose (or any multi-service orchestration) — this app
  has no other infrastructure dependency today (no DB, no cache, no mock
  service); adding a compose file and requiring `docker compose` /
  `podman-compose` on top of the base tool would be complexity with no
  current payoff.
- Absolute `CHATBOT_COMMANDS_FILE` paths (see the volume-mount note
  above).
- Publishing the built image to a registry, or any CI integration for the
  container path — this is a local dev convenience, not a deployment
  story.
