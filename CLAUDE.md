# CLAUDE.md

@AGENTS.md

## Claude Code specifics

- **Skills** in `.claude/skills/`:
  - `screenshot-tour`: run the tour and review the screens.
  - `add-content`: add a crop, animal, item, machine or recipe end to end.
  - `release`: tag a version and ship the APK.
- **Subagents** in `.claude/agents/`:
  - `balance-reviewer`: checks economy changes in `ItemDb` and `Farm`.
  - `ui-reviewer`: checks panel and HUD changes against the tour screenshots.
- **Hook:** editing `tools/i18n/strings.py` regenerates `assets/i18n/*.properties` automatically (`.claude/hooks/regen-strings.sh`).
- **Cloud sessions:** Google's Maven repository may be blocked there. In that case the Android module can't build locally. Rely on CI for the APK and the emulator smoke test, and say so.
- **Personal overrides** go in `CLAUDE.local.md` and `.claude/settings.local.json`. Both are git-ignored.
