#!/usr/bin/env bash
# Installs the APK on the running emulator, starts the game, taps into a farm and checks the
# app is still alive with no fatal exception. Leaves screenshots and logcat in smoke/.
set -euo pipefail

APK_DIR="${1:-apk}"
PKG="dev.mursal.dewfield"
OUT="smoke"
mkdir -p "$OUT"

apk=$(ls "$APK_DIR"/*.apk | head -n 1)
echo "Installing $apk"
adb install -r "$apk"
adb logcat -c

alive() {
  adb shell pidof "$PKG" > /dev/null 2>&1
}

crashed() {
  adb logcat -d > "$OUT/logcat.txt"
  grep -A 2 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "Process: $PKG" && return 0
  grep -qE "Fatal signal.*\($PKG\)|ANR in $PKG" "$OUT/logcat.txt"
}

fail() {
  echo "::error::$1"
  adb logcat -d > "$OUT/logcat.txt" || true
  grep -A 30 -E "FATAL EXCEPTION|Fatal signal|ANR in" "$OUT/logcat.txt" || tail -n 120 "$OUT/logcat.txt"
  exit 1
}

adb shell am start -W -n "$PKG/.android.AndroidLauncher"
sleep 20
adb exec-out screencap -p > "$OUT/1-title.png"
alive || fail "The app is not running after start"
crashed && fail "Crash on start"

# Portrait size: the smaller dimension is the width. On a fresh install the title shows one big
# "New farm" button whose centre sits 230 UI units above the bottom edge (see TitleScreen.build).
# The UI is a 720x1280 ExtendViewport, so a unit is w/720 pixels on phones taller than 9:16 and
# h/1280 on squatter screens.
size=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1)
read -r a b <<< "$size"
w=$(( a < b ? a : b ))
h=$(( a < b ? b : a ))
if (( h * 720 >= w * 1280 )); then
  new_farm_y=$(( h - 230 * w / 720 ))
else
  new_farm_y=$(( h - 230 * h / 1280 ))
fi
adb shell input tap $(( w / 2 )) "$new_farm_y"
sleep 15
adb exec-out screencap -p > "$OUT/2-game.png"
alive || fail "The app died after entering the game"
crashed && fail "Crash after entering the game"

# Walk somewhere and open the pause menu with Back, then close it again.
adb shell input tap $(( w / 2 )) $(( h * 3 / 4 ))
sleep 4
adb shell input keyevent KEYCODE_BACK
sleep 2
adb exec-out screencap -p > "$OUT/3-pause.png"
adb shell input keyevent KEYCODE_BACK
sleep 2
alive || fail "The app died after walking and Back"
crashed && fail "Crash after walking and Back"

adb logcat -d > "$OUT/logcat.txt"
echo "Smoke test passed"
