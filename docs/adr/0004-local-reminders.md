# ADR 0004: Local reminders

- **Status:** accepted (2026-11-24)

## Context

The owner wants players to come back more often. A reminder when crops are ripe or a gift is waiting
does that, but Dewfield is offline, has no accounts and no ads, and must stay that way (AGENTS.md:
no network calls, no analytics). The game clock also only runs while the game is open, so a reminder
can't promise "your crops will be ready at 15:00".

## Decision

- **Local notifications only.** No push service, no server, no Firebase. Nothing leaves the phone.
- **Planned when the game goes to the background.** `DewfieldGame.pause()` asks the pure
  `Reminders.plan(farm, now, zone)` for what to say, translates it with the current language, and hands
  it to the platform through the `Notifier` interface. `resume()` and `create()` cancel everything.
- **Only true things.** Because game time stands still while away, farm news describes the state the
  player left: ripe crops, goods waiting, finished machines, a low trough. Plus the daily gift and a
  nudge after 3 days away. At most one of each kind, at least 6 hours apart, never 21:00–09:00.
- **Opt-in, asked in context.** Reminders are off by default. The game offers them once, right after
  the first daily gift is opened (the moment they obviously help), and the menu has a switch.
  On Android 13+ that is when `POST_NOTIFICATIONS` is requested. If the player denied it for good, the
  switch opens the app's notification settings instead of asking again.
- **Android implementation without new dependencies:** `AlarmManager.setAndAllowWhileIdle` (inexact, so
  no exact-alarm permission), a non-exported `ReminderReceiver`, the planned notices stored in
  `SharedPreferences` so `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` can set them again, and plain
  framework `Notification.Builder` with a channel on API 26+. No androidx.
- **The daily gift uses real calendar days** in the phone's time zone (`DailyGift`, `LocalClock`,
  without java.time because minSdk is 24). Missing days never resets it, and a clock turned back can't
  claim twice.

## Consequences

- The app now asks for `POST_NOTIFICATIONS` (only when the player turns reminders on) and
  `RECEIVE_BOOT_COMPLETED`. The README and deployment notes say so.
- Desktop has no notifications (`Notifier.None`); the screenshot tour uses `Tour.PreviewNotifier` so the
  reminder UI still gets checked.
- Reminder texts are normal i18n keys (`notify.*`), so `StringsTest` covers them in both languages.
- A reminder can arrive a little late (inexact alarms, Doze). That is fine for a cozy game.
- The emulator smoke test never sees a permission dialog, because a new farm doesn't ask.
