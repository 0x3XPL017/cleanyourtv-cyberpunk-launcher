#!/system/bin/sh
# SamuraiOS Memory Optimization Helper Daemon
# Runs as UID 2000 (shell) to perform privileged cleanup
while read -r line; do
  case "$line" in
    PURGE*)
      before_free=$(grep MemAvailable /proc/meminfo | tr -dc '0-9')
      killed_count=0
      for p in $(pm list packages -3 | cut -d: -f2) com.xiaomi.mitv.mediaexplorer com.mitv.gallery com.xiaomi.mitv.smartshare com.android.tv.settings; do
        if [ "$p" != "tv.samurai.home" ] && [ "$p" != "flar2.homebutton" ]; then
          if pidof "$p" >/dev/null 2>&1; then
            am kill "$p" >/dev/null 2>&1
            killed_count=$((killed_count + 1))
          fi
        fi
      done
      am kill-all >/dev/null 2>&1
      sync
      sleep 0.4
      after_free=$(grep MemAvailable /proc/meminfo | tr -dc '0-9')
      freed=$((after_free - before_free))
      if [ $freed -lt 0 ]; then freed=0; fi
      echo "OK:KILLED:$killed_count:FREED_KB:$freed"
      ;;
    PING)
      echo "PONG"
      ;;
    *)
      echo "ERR:UNKNOWN_CMD"
      ;;
  esac
done
