# Shared settings for every script in tools/. Source it, don't run it.
#
# The computer talks to the TV over ADB, either through a USB cable or over Wi-Fi. The choice lives in
# the environment or in tools/tv.conf (gitignored). tools/connect.sh asks and writes it for you.
#
#   Wi-Fi:  TV_CONNECTION=wifi  TV_IP=192.0.2.10   [TV_PORT=5555]
#   USB:    TV_CONNECTION=usb   [TV_SERIAL=<serial from "adb devices">]   (only needed with 2+ devices)
#
#   TV_IP=192.0.2.10 tools/reapply.sh                      # one-off, Wi-Fi
#   TV_CONNECTION=usb tools/reapply.sh                     # one-off, USB
#   tools/connect.sh                                       # ask, test and remember in tools/tv.conf
#
# Values in the environment win over tools/tv.conf. Setting only TV_IP implies Wi-Fi, setting only
# TV_SERIAL implies USB (so old tv.conf files with just TV_IP=... keep working).
#
# Scripts that never talk to the TV (build-install.sh --build-only, connect.sh) set NEED_TV=0 before
# sourcing.

TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TOOLS/.." && pwd)"
STATE="$ROOT/state"
PKG=tv.samurai.home
HOME_ACTIVITY="$PKG/.MainActivity"

# Git Bash on Windows rewrites arguments that look like paths (/data/local/tmp -> C:/Program Files/Git/...).
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) export MSYS_NO_PATHCONV=1 ;; esac

# --- Connection settings: environment first, then tools/tv.conf.
_env_conn="${TV_CONNECTION:-}"; _env_ip="${TV_IP:-}"; _env_port="${TV_PORT:-}"; _env_serial="${TV_SERIAL:-}"
if [ -f "$TOOLS/tv.conf" ]; then
  # shellcheck disable=SC1091
  . "$TOOLS/tv.conf"
fi
if [ -z "$_env_conn" ]; then
  # A one-off TV_IP=... or TV_SERIAL=... on the command line picks its transport even if tv.conf says otherwise.
  if [ -n "$_env_ip" ]; then TV_CONNECTION=wifi
  elif [ -n "$_env_serial" ]; then TV_CONNECTION=usb; fi
else
  TV_CONNECTION="$_env_conn"
fi
[ -n "$_env_ip" ] && TV_IP="$_env_ip"
[ -n "$_env_port" ] && TV_PORT="$_env_port"
[ -n "$_env_serial" ] && TV_SERIAL="$_env_serial"
unset _env_conn _env_ip _env_port _env_serial

TV_PORT="${TV_PORT:-5555}"
if [ -z "${TV_CONNECTION:-}" ]; then
  if [ -n "${TV_IP:-}" ]; then TV_CONNECTION=wifi
  elif [ -n "${TV_SERIAL:-}" ]; then TV_CONNECTION=usb; fi
fi
TV_CONNECTION="$(printf '%s' "${TV_CONNECTION:-}" | tr '[:upper:]' '[:lower:]')"
case "$TV_CONNECTION" in
  wi-fi|wlan|network|tcp) TV_CONNECTION=wifi ;;
  cable) TV_CONNECTION=usb ;;
esac

if [ "${NEED_TV:-1}" = 1 ]; then
  case "$TV_CONNECTION" in
    usb) ;;
    wifi)
      if [ -z "${TV_IP:-}" ]; then
        cat >&2 <<'EOF'
TV_CONNECTION=wifi but TV_IP is not set.
  Find the TV's address on the TV: Settings > Network & Internet > (your network) > IP address.
  Then run:   tools/connect.sh wifi <address>
  or one-off: TV_IP=<address> tools/<script>.sh ...
EOF
        exit 1
      fi ;;
    "")
      cat >&2 <<'EOF'
No connection chosen. How should this computer talk to the TV?
  USB cable:  tools/connect.sh usb               (TV plugged into this computer, USB debugging on)
  Wi-Fi:      tools/connect.sh wifi <tv address> (same network, Network/ADB debugging on)
  or just:    tools/connect.sh                   (asks you)
EOF
      exit 1 ;;
    *)
      echo "TV_CONNECTION='$TV_CONNECTION' is not valid: use 'usb' or 'wifi' (tools/connect.sh)." >&2
      exit 1 ;;
  esac
fi

# adb: the repo's own platform-tools/ first (unzip Google's platform-tools there), else the one on PATH.
if [ -x "$ROOT/platform-tools/adb.exe" ]; then ADB_BIN="$ROOT/platform-tools/adb.exe"
elif [ -x "$ROOT/platform-tools/adb" ]; then ADB_BIN="$ROOT/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then ADB_BIN="$(command -v adb)"
elif [ "${NEED_TV:-1}" = 1 ]; then
  echo "adb not found. Unzip Android platform-tools into $ROOT/platform-tools/ or put adb on your PATH:" >&2
  echo "  https://developer.android.com/tools/releases/platform-tools" >&2
  exit 1
fi

# TV is the adb serial every command targets: <ip>:<port> over Wi-Fi, the USB serial over USB
# (found by adb_connect when TV_SERIAL is not set).
if [ "$TV_CONNECTION" = usb ]; then TV="${TV_SERIAL:-}"; else TV="${TV_IP:-}:$TV_PORT"; fi

# USB devices adb sees, one "<serial><TAB><state>" per line. Network (ip:port), wireless-debugging
# (mDNS) and emulator entries are left out.
usb_devices() {
  "$ADB_BIN" devices 2>/dev/null | tr -d '\r' | sed '1d' \
    | awk -F'\t' 'NF >= 2 && $1 !~ /:/ && $1 !~ /\._adb-tls/ && $1 !~ /^emulator-/ { print $1 "\t" $2 }'
}

adb_connect_wifi() {
  local out
  TV="$TV_IP:$TV_PORT"
  out="$("$ADB_BIN" connect "$TV" 2>&1 | tr -d '\r')"
  case "$out" in
    *"connected to"*) ;;
    *) echo "adb connect $TV: $out" >&2
       echo "Is Network/ADB debugging on, and did you accept 'Allow USB debugging?' on the TV?" >&2
       echo "(Or connect with a USB cable instead: tools/connect.sh usb)" >&2
       exit 1 ;;
  esac
  # A fresh network connection can be "unauthorized" until the prompt on the TV is accepted.
  local state; state="$("$ADB_BIN" -s "$TV" get-state 2>&1 | tr -d '\r' || true)"
  case "$state" in
    device) ;;
    *unauthorized*)
      echo "$TV is unauthorized: accept 'Allow USB debugging?' on the TV (tick 'Always allow'), then run again." >&2
      exit 1 ;;
  esac
}

adb_connect_usb() {
  local list count state
  list="$(usb_devices || true)"
  if [ -n "${TV_SERIAL:-}" ]; then
    state="$(printf '%s\n' "$list" | awk -F'\t' -v s="$TV_SERIAL" '$1 == s { print $2 }')"
  else
    count="$(printf '%s\n' "$list" | grep -c . || true)"
    if [ "${count:-0}" -gt 1 ]; then
      echo "More than one USB device is connected:" >&2
      printf '%s\n' "$list" | sed 's/^/  /' >&2
      echo "Pick the TV: tools/connect.sh usb <serial>   (or TV_SERIAL=<serial> tools/<script>.sh)" >&2
      exit 1
    fi
    TV_SERIAL="$(printf '%s\n' "$list" | head -1 | cut -f1)"
    state="$(printf '%s\n' "$list" | head -1 | cut -f2)"
  fi
  case "$state" in
    device) TV="$TV_SERIAL" ;;
    unauthorized)
      echo "USB device $TV_SERIAL is unauthorized: accept 'Allow USB debugging?' on the TV (tick 'Always allow'), then run again." >&2
      exit 1 ;;
    offline)
      echo "USB device $TV_SERIAL is offline: unplug and replug the cable, or toggle USB debugging off and on." >&2
      exit 1 ;;
    "")
      if [ -n "${TV_SERIAL:-}" ]; then echo "USB device $TV_SERIAL not found." >&2
      else echo "No TV found over USB." >&2; fi
      cat >&2 <<'EOF'
  - Is USB debugging on (Settings > System > Developer options)?
  - Is the cable a data cable, in the TV's debug-capable port? Many TVs' USB-A ports are host-only
    (for drives) and never show up; streaming boxes/sticks with USB-C usually work.
  - Windows: some devices need the Google USB driver (or the vendor's) before "adb devices" lists them.
  - Check with: adb devices
  - No luck? Use Wi-Fi instead: tools/connect.sh wifi <tv address>
EOF
      exit 1 ;;
    *)
      echo "USB device $TV_SERIAL is in state '$state'; expected 'device'. See docs/TROUBLESHOOTING.md#connecting" >&2
      exit 1 ;;
  esac
}

adb_connect() {
  if [ "$TV_CONNECTION" = usb ]; then adb_connect_usb; else adb_connect_wifi; fi
}

adb_tv() {
  # Over USB without TV_SERIAL the serial is only known after adb_connect; find it on first use.
  [ -n "$TV" ] || adb_connect
  "$ADB_BIN" -s "$TV" "$@"
}
# One shell command on the TV with Windows line endings stripped.
tv_sh() { adb_tv shell "$@" 2>&1 | tr -d '\r'; }
# Windows adb.exe needs C:/... paths for local files; elsewhere the path is used as is.
local_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s\n' "$1"; fi; }
mkdir -p "$STATE"
