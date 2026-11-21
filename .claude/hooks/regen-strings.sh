#!/usr/bin/env sh
# PostToolUse hook: when tools/i18n/strings.py was edited, regenerate assets/i18n/*.properties
# so the game and the tests always see the current strings. Reads the hook JSON on stdin.
input=$(cat)
case "$input" in
  *tools/i18n/strings.py*)
    cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
    if ! python3 tools/i18n/strings.py >&2; then
      echo "strings.py failed: fix the syntax error above, the .properties files were not updated" >&2
      exit 2
    fi
    ;;
esac
exit 0
