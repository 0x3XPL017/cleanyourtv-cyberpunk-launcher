#!/usr/bin/env bash
# Choose how this computer talks to the TV, test it, and remember it in tools/tv.conf (gitignored).
# Every other script reads tools/tv.conf, so you only do this once per TV (or when you switch).
#
# Usage: tools/connect.sh                    ask: USB cable or Wi-Fi?
#        tools/connect.sh usb [serial]       USB cable (serial only needed with 2+ USB devices)
#        tools/connect.sh wifi <ip> [port]   Wi-Fi / network (port defaults to 5555)
#
#   USB   - TV (or Google TV box/stick) plugged into this computer, USB debugging on. No network
#           needed, nothing listens on the network, and it survives Wi-Fi changes. Needs a port on the
#           TV that supports USB debugging: many TVs' USB-A ports are host-only (drives) and never work.
#   Wi-Fi - same network as the TV, Network/ADB debugging on. Works with every Android TV, no cable,
#           but port 5555 is open (unencrypted) on your network until you turn debugging off again.
#
# It prints the model and Android version it found, which also proves the connection works.
set -uo pipefail
NEED_TV=0
source "$(dirname "$0")/env.sh"

[ -n "${ADB_BIN:-}" ] || {
  echo "adb not found. Unzip Android platform-tools into $ROOT/platform-tools/ or put adb on your PATH:" >&2
  echo "  https://developer.android.com/tools/releases/platform-tools" >&2
  exit 1
}

mode="${1:-}"
mode="$(printf '%s' "$mode" | tr '[:upper:]' '[:lower:]')"
case "$mode" in wi-fi|network|tcp) mode=wifi ;; cable) mode=usb ;; esac
_prev_ip="${TV_IP:-}"
interactive=0
pin_serial=0

if [ -z "$mode" ]; then
  interactive=1
  cur="${TV_CONNECTION:-none}"
  cat <<EOF
How should this computer connect to the TV?   (currently: $cur)

  1) USB cable  - TV plugged into this computer with a data cable, USB debugging on.
                  Private (nothing open on the network), no IP address needed.
                  Only works if the TV has a debug-capable port (most streaming boxes/sticks with
                  USB-C do; many TVs' USB-A ports are for drives only).
  2) Wi-Fi      - TV and computer on the same network, Network/ADB debugging on.
                  Works on every Android TV, no cable; needs the TV's IP address.

EOF
  while :; do
    printf 'Choose 1 (USB) or 2 (Wi-Fi): '
    read -r answer || exit 1
    case "$(printf '%s' "$answer" | tr '[:upper:]' '[:lower:]')" in
      1|usb|u) mode=usb; break ;;
      2|wifi|wi-fi|w) mode=wifi; break ;;
      *) echo "Please type 1 or 2." ;;
    esac
  done
fi

case "$mode" in
  usb)
    TV_CONNECTION=usb
    TV_SERIAL="${2:-}"
    [ -n "$TV_SERIAL" ] && pin_serial=1
    TV_IP=""; TV_PORT=5555
    if [ "$interactive" = 1 ]; then
      # Interactive: list USB devices and let the user pick when there is more than one.
      echo
      echo "Plug the TV into this computer and turn on USB debugging. Watch the TV for"
      echo "'Allow USB debugging?' and accept it (tick 'Always allow')."
      printf 'Press Enter when ready... '; read -r _ || true
      list="$(usb_devices || true)"
      n="$(printf '%s\n' "$list" | grep -c . || true)"
      if [ "${n:-0}" -gt 1 ]; then
        echo "USB devices found:"
        printf '%s\n' "$list" | awk -F'\t' '{ printf "  %d) %s (%s)\n", NR, $1, $2 }'
        printf 'Which one is the TV? '
        read -r pick || exit 1
        TV_SERIAL="$(printf '%s\n' "$list" | sed -n "${pick}p" | cut -f1)"
        [ -n "$TV_SERIAL" ] || { echo "No such device." >&2; exit 1; }
        pin_serial=1
      fi
    fi
    TV="${TV_SERIAL:-}"
    adb_connect
    ;;
  wifi)
    TV_CONNECTION=wifi
    TV_SERIAL=""
    TV_IP="${2:-}"
    TV_PORT="${3:-5555}"
    if [ -z "$TV_IP" ]; then
      echo
      echo "Find the TV's IP address on the TV: Settings > Network & Internet > (your network)."
      echo "Turn on Network debugging (or 'ADB debugging') in Settings > System > Developer options."
      printf 'TV IP address%s: ' "${_prev_ip:+ [$_prev_ip]}"
      read -r TV_IP || exit 1
      [ -n "$TV_IP" ] || TV_IP="$_prev_ip"
    fi
    [ -n "$TV_IP" ] || { echo "No IP address given." >&2; exit 1; }
    echo "Connecting to $TV_IP:$TV_PORT; watch the TV for 'Allow USB debugging?' and accept it."
    adb_connect
    ;;
  *)
    echo "Usage: $0 [usb [serial] | wifi <ip> [port]]" >&2
    exit 2 ;;
esac

# Remember the choice. Only what this transport needs is written.
{
  echo "# Written by tools/connect.sh on $(date '+%F %T'). Run it again to switch between USB and Wi-Fi."
  echo "TV_CONNECTION=$TV_CONNECTION"
  if [ "$TV_CONNECTION" = usb ]; then
    # Pinned only when chosen; otherwise the single USB device is found each time.
    if [ "$pin_serial" = 1 ]; then echo "TV_SERIAL=$TV_SERIAL"; fi
  else
    echo "TV_IP=$TV_IP"
    [ "$TV_PORT" = 5555 ] || echo "TV_PORT=$TV_PORT"
  fi
} > "$TOOLS/tv.conf"

model="$(tv_sh getprop ro.product.model)"
release="$(tv_sh getprop ro.build.version.release)"
echo "connected over $TV_CONNECTION ($TV): $model, Android $release"
echo "saved to tools/tv.conf"
