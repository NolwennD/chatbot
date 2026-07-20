#!/bin/sh
# Ensures the Twitch chatbot environment variables are set, then runs ./mvnw.
# Missing mandatory variables are always prompted for.
#
#   ./setup-env.sh                          # prompt only for what's missing, then run ./mvnw
#   ./setup-env.sh -u|--update              # review and override every variable, even if already set
#   eval "$(./setup-env.sh -c|--clear)"     # unset every variable in your current shell
#   ./setup-env.sh --container              # run via Docker or Podman (auto-detected) instead of ./mvnw
#   ./setup-env.sh --container=docker       # same, forcing Docker specifically
#   ./setup-env.sh --container=podman       # same, forcing Podman specifically
#
# --clear also removes any saved block from your shell profile, and prints unset commands: a
# subprocess cannot remove variables from the shell that started it, so eval is what actually
# applies that part to your session.
#
# If any value had to be typed, you're offered to save it to your shell profile so it persists
# across sessions. Declining still launches the app with the values just entered.

PROFILE_BEGIN='# >>> chatbot setup-env >>>'
PROFILE_END='# <<< chatbot setup-env <<<'

profile_file() {
  case "${SHELL:-}" in
    */zsh) echo "$HOME/.zshrc" ;;
    */bash) echo "$HOME/.bashrc" ;;
    *) echo "$HOME/.profile" ;;
  esac
}

strip_profile_block() {
  profile=$(profile_file)

  if [ -f "$profile" ] && grep -qF "$PROFILE_BEGIN" "$profile"; then
    tmp="$profile.setup-env.tmp"
    sed "/^$PROFILE_BEGIN\$/,/^$PROFILE_END\$/d" "$profile" > "$tmp"
    mv "$tmp" "$profile"
    echo "Removed saved values from $profile" >&2
  fi
}

update=0
prompted=0
container_requested=0
container=""

case "${1:-}" in
  -u | --update)
    update=1
    shift
    ;;
  -c | --clear)
    strip_profile_block
    echo 'unset TWITCH_CHANNEL TWITCH_BOT_USERNAME TWITCH_OAUTH_TOKEN CHATBOT_COMMANDS_FILE CHATBOT_LOCALE CHATBOT_CONTAINER_TOOL'
    exit 0
    ;;
  --container)
    container_requested=1
    shift
    ;;
  --container=*)
    container_requested=1
    container=${1#--container=}
    shift
    ;;
esac

ask() {
  var_name=$1
  prompt=$2
  eval "current=\${$var_name:-}"

  if [ -n "$current" ] && [ "$update" -eq 0 ]; then
    export "$var_name"
    return 0
  fi

  if [ -n "$current" ]; then
    printf '%s [%s]: ' "$prompt" "$current"
  else
    printf '%s: ' "$prompt"
  fi
  read -r answer

  if [ -z "$answer" ] && [ -n "$current" ]; then
    answer=$current
  fi

  eval "$var_name=\"\$answer\""
  export "$var_name"
  prompted=1
}

ask_secret() {
  var_name=$1
  prompt=$2
  eval "current=\${$var_name:-}"

  if [ -n "$current" ] && [ "$update" -eq 0 ]; then
    export "$var_name"
    return 0
  fi

  if [ -n "$current" ]; then
    printf '%s [leave blank to keep current]: ' "$prompt"
  else
    printf '%s: ' "$prompt"
  fi

  if [ -t 0 ]; then
    stty -echo
  fi
  read -r answer
  if [ -t 0 ]; then
    stty echo
    printf '\n'
  fi

  if [ -z "$answer" ] && [ -n "$current" ]; then
    answer=$current
  fi

  eval "$var_name=\"\$answer\""
  export "$var_name"
  prompted=1
}

offer_save() {
  profile=$(profile_file)

  printf 'Save these values to %s so they persist across sessions? [y/N]: ' "$profile"
  read -r save_answer
  case "$save_answer" in
    y | Y | yes | YES) ;;
    *) return 0 ;;
  esac

  tmp="$profile.setup-env.tmp"

  if [ -f "$profile" ]; then
    sed "/^$PROFILE_BEGIN\$/,/^$PROFILE_END\$/d" "$profile" > "$tmp"
  else
    : > "$tmp"
  fi

  {
    cat "$tmp"
    echo "$PROFILE_BEGIN"
    echo "export TWITCH_CHANNEL=\"$TWITCH_CHANNEL\""
    echo "export TWITCH_BOT_USERNAME=\"$TWITCH_BOT_USERNAME\""
    echo "export TWITCH_OAUTH_TOKEN=\"$TWITCH_OAUTH_TOKEN\""
    echo "export CHATBOT_COMMANDS_FILE=\"$CHATBOT_COMMANDS_FILE\""
    [ -n "$CHATBOT_LOCALE" ] && echo "export CHATBOT_LOCALE=\"$CHATBOT_LOCALE\""
    [ -n "$CHATBOT_CONTAINER_TOOL" ] && echo "export CHATBOT_CONTAINER_TOOL=\"$CHATBOT_CONTAINER_TOOL\""
    echo "$PROFILE_END"
  } > "$profile"
  rm -f "$tmp"

  echo "Saved to $profile (open a new shell, or run '. $profile', for it to apply there)."
}

tool_usable() {
  command -v "$1" >/dev/null 2>&1 && "$1" info >/dev/null 2>&1
}

validate_container_tool() {
  if ! tool_usable "$container"; then
    echo "$container is not available and working." >&2
    exit 1
  fi
}

detect_container_tool() {
  docker_ok=0
  podman_ok=0
  tool_usable docker && docker_ok=1
  tool_usable podman && podman_ok=1

  if [ "$docker_ok" -eq 1 ] && [ "$podman_ok" -eq 0 ]; then
    container=docker
    return 0
  fi

  if [ "$podman_ok" -eq 1 ] && [ "$docker_ok" -eq 0 ]; then
    container=podman
    return 0
  fi

  if [ "$docker_ok" -eq 0 ] && [ "$podman_ok" -eq 0 ]; then
    echo 'Neither docker nor podman is available and working.' >&2
    exit 1
  fi

  if [ -n "${CHATBOT_CONTAINER_TOOL:-}" ]; then
    container=$CHATBOT_CONTAINER_TOOL
    return 0
  fi

  printf 'Both docker and podman are available. Which one do you want to use? [docker/podman]: '
  read -r container_answer
  case "$container_answer" in
    docker | podman) container=$container_answer ;;
    *)
      echo 'Please answer "docker" or "podman".' >&2
      exit 1
      ;;
  esac

  CHATBOT_CONTAINER_TOOL=$container
  prompted=1
}

# Trailing args ("$@") are Maven-specific (forwarded to ./mvnw in the
# default path) and don't apply to a container run, so they're not
# forwarded here.
run_container() {
  "$container" build -t chatbot .
  "$container" run --rm \
    -e TWITCH_CHANNEL -e TWITCH_BOT_USERNAME -e TWITCH_OAUTH_TOKEN -e CHATBOT_LOCALE \
    -e CHATBOT_COMMANDS_FILE \
    -v "$PWD/$CHATBOT_COMMANDS_FILE:/app/$CHATBOT_COMMANDS_FILE" \
    chatbot
}

ask TWITCH_CHANNEL "Twitch channel name"
ask TWITCH_BOT_USERNAME "Twitch bot account username"
ask_secret TWITCH_OAUTH_TOKEN "Twitch OAuth token (no 'oauth:' prefix)"

: "${CHATBOT_COMMANDS_FILE:=commands.txt}"
ask CHATBOT_COMMANDS_FILE "Commands file path"
ask CHATBOT_LOCALE "Locale (fr or en, blank = auto-detect from system)"

if [ "$container_requested" -eq 1 ]; then
  if [ -n "$container" ]; then
    validate_container_tool
  else
    detect_container_tool
  fi
fi

if [ "$prompted" -eq 1 ]; then
  offer_save
fi

if [ -n "$container" ]; then
  run_container
else
  exec ./mvnw "$@"
fi
