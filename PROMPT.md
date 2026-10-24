# The prompt
You are setting up my Android TV with the Cyberpunk 2077 SamuraiOS launcher in this repo: debloat it safely, install the launcher customised to my answers, and verify everything with screenshots from the TV. Work on the real device, in small steps, and stop to ask me whenever something depends on my preferences. Everything you change must stay reversible.

This repo already has what you need. Read `README.md`, `docs/TROUBLESHOOTING.md` and any existing configuration files before starting. Use the scripts in `tools/` rather than writing your own; the primary file in the launcher you should edit is `launcher/app/src/main/res/values/config.xml`. All per-TV output goes in `state/` (gitignored).

## 1. Connect

- **First ask me how to connect: USB cable or Wi-Fi** (use AskUserQuestion when you have it). Explain briefly: USB is private and needs no IP, but only works if my TV/box has a USB-debug-capable port (most USB-C streaming boxes/sticks do; many TVs' USB-A ports are host-only); Wi-Fi works on every Android TV but leaves port 5555 open on my network until debugging is switched off at the end.
- Use `platform-tools/` in the repo if adb isn't installed: tell me where to download Android platform-tools and unzip it there. Tell me **before** the "Allow USB debugging?" prompt appears so I can accept it (it appears for both USB and Wi-Fi).
- Run `tools/connect.sh usb` or `tools/connect.sh wifi <ip>`. It tests the connection and writes my choice to `tools/tv.conf` (`TV_CONNECTION=usb` or `TV_CONNECTION=wifi` + `TV_IP=...`). Every script reads it from there. Don't run `tools/connect.sh` without arguments yourself: that mode waits for keyboard input and is meant for me.
- **If USB:**
  - Ask me to plug the TV into this computer and turn on **USB debugging**, then run `tools/connect.sh usb`.
  - If it reports several USB devices, show me the list (`adb devices -l`), ask which is the TV and run `tools/connect.sh usb <serial>`.
  - If no device shows up: check it is a data cable and try the TV's other ports; on Windows the device may need the Google USB driver or the vendor's. If it still doesn't appear, tell me my TV probably has no USB-debug port and offer to **switch to Wi-Fi**.
  - Don't unplug the cable during the setup; if the TV disconnects after a reboot, wait for it to boot and run the last command again.
- **If Wi-Fi:**
  - Ask me for the TV's IP address (or find it from the router/ARP table and confirm with me), then run `tools/connect.sh wifi <ip>` (it does `adb connect <ip>:5555`).
  - If port 5555 refuses, the TV may only offer **Wireless debugging**: ask me to open Developer options > Wireless debugging > Pair device with pairing code, then `adb pair <ip>:<port>` with the code, `adb connect` to the port shown, and optionally `adb tcpip 5555`.
  - If Wi-Fi keeps failing and the device has a USB debug port, offer to switch to USB.
- On Windows Git Bash, set `MSYS_NO_PATHCONV=1` for any adb command you type by hand, or device paths like `/data/local/tmp` get rewritten. (`tools/env.sh` does this for the scripts.) When you type adb by hand, target the TV with `-s <serial>` (USB) or `-s <ip>:5555` (Wi-Fi).
- Never use `adb install`: on Android 14 it can hang waiting for an invisible prompt. Push the APK to `/data/local/tmp` and run `pm install -r` (`tools/build-install.sh` does this).
- Read `getprop ro.product.model`, `ro.build.version.release` and the RAM, and tell me what you found. Write the connection type to `state/choices.md`.

## 2. Back up before changing anything

Run `tools/backup.sh`. It saves package lists, HOME apps, `getprop` and all settings to `state/backup-<date>/`. Check that the folder is not empty. `tools/restore.sh` uses this backup to put Private DNS, the screensaver, and original packages back.

## 3. Ask me

Ask these as checklist questions (use AskUserQuestion when you have it), a few at a time:

**About the TV**
1. TV model (confirm what getprop said) and what is plugged into its USB ports. Some TVs cut USB power while debugging is on; warn me if mine is one of them. If we connect over USB, the cable takes one of those ports during setup: tell me if something needs unplugging.
2. Which streaming apps do I use? Show me the installed ones as a checklist; I tick what to keep.
3. Keep or remove: voice assistant, Chromecast built-in, AirPlay, smart-home / Alexa integrations, Ambient mode screensaver, firmware update from USB. Default to keep for anything I'm unsure of.
4. Which six apps go into the primary **Data Shard dock** row, and in which order (left to right)? (Default: YouTube, Netflix, Prime Video, Plex, Browser, Settings).

**The Cyberpunk Widgets & HUD**
5. **Weather & Environmental Telemetry**: Which **city**? Look up its coordinates (latitude and longitude with 4 decimals) for the Open-Meteo weather integration. Also confirm temperature units (°C or °F).
6. **Now Playing Samurai Audio Deck**:
   - Confirm whether to keep the continuous animated Cyberpunk demo stream (`CHIPPIN' IN // NIGHT CITY FM 107.3`).
   - Mention that USB drives and local music files are automatically scanned and playable.
7. **Privileged Memory Optimizer**:
   - Confirm setting up the background RAM helper daemon (`tools/samurai_helper.sh`, running as UID 2000) for real-time `/proc/meminfo` telemetry and one-click RAM purging.

**The TV's settings**
8. **Ad-blocking Private DNS** (`dns.adguard-dns.com`)? Explain it blocks many in-app ads and trackers but **not YouTube ads**, and can occasionally break an app's login.
9. **Screen-off timeout** when idle (for example 10 or 30 minutes, or never)?

Write my answers to `state/choices.md` as you go, so a later session (or a factory reset) can repeat them.

## 4. Debloat, carefully

- List packages for user 0 (`pm list packages --user 0`, plus `-s`, `-3`, `-d`) and sort them into:
  vendor telemetry/analytics, vendor ads/promotions/content rows, background extras (demo modes, help, duplicate cast/share services), unused system apps (camera, gallery, print, calendar sync), preloaded streaming apps, and **keep**. Show me the plan as a table before disabling anything.
- **Never disable** what the TV needs: Live TV and HDMI/tuner inputs and their providers, the input picker, remote/Bluetooth/voice-remote services, the on-screen keyboard(s), Play Store and Play services, Settings, system UI, and any `FallbackHome` activity. Vendor names are cryptic: run `dumpsys package <name>` (activities, services, receivers) before guessing, and ask me if still unsure.
- **Never launch** a factory, service-menu or test package (names with `factory`, `servicemenu`, `fac`, `AT`, `hkcfac` and similar) to see what it does, not even read-only. On some boards opening one flips a factory flag that breaks inputs on the next boot. `dumpsys package` is enough.
- Disable with `tools/disable.sh <batch-name> pkg ...` (one `pm disable-user --user 0` per package, logged to `state/disabled.txt`). Never uninstall a system package.
- Small batches, one category at a time. After each batch take a screenshot with `tools/shot.sh` and look at it, and check that the TV's home, inputs and one app still work. A batch can take something load-bearing with it.
- Only touch streaming apps, the assistant, Chromecast and update services according to my answers.
- Apply my Private DNS choice (`settings put global private_dns_specifier dns.adguard-dns.com` then `settings put global private_dns_mode hostname`), open an app to check it still plays, and my screen-off timeout (`settings put system screen_off_timeout <ms>`).
- After the last batch, **reboot once** and compare `pm list packages -d --user 0` with `state/disabled.txt`. Packages the firmware turned back on by itself go on the `# firmware-reenables:` line at the top of `state/disabled.txt` (space separated) and, if they show up as apps, into secondary matrix. Don't fight them after every boot.

## 5. Configure and install the launcher

- Edit `launcher/app/src/main/res/values/config.xml` with my answers: `weather_city_label`, `weather_lat`, `weather_lng`, `weather_units`, `weather_cyber_flavor`, and `dock_packages` (the 6 primary Data Shards). Show me the diff.
- Run `tools/build-install.sh`. It compiles the APK, pushes it to the TV, installs it via `pm install -r`, starts the background privileged RAM optimization helper daemon (`tools/samurai_helper.sh`), and activates `tv.samurai.home/.MainActivity` as HOME.
- If it reports missing JDK 17 or Android SDK pieces, tell me exactly what to install (or download the portable versions into `toolchain/` with my OK) and run it again.
- **Home takeover** (follow `docs/TROUBLESHOOTING.md#home-takeover`):
  - `set-home-activity` alone can print Success and change nothing on Google TV.
  - Disable the stock launcher with `tools/disable.sh home <stock launcher package>`.
  - If the setup wizard (`com.google.android.tungsten.setupwraith`) then grabs HOME, first run `dumpsys wifi | grep -o 'cname=[^ ]*'`. If any saved network belongs to setupwraith, **stop**: disabling it would delete that network and drop the TV off Wi-Fi (and off ADB, if we're connected over Wi-Fi). Ask me to forget and re-join the network in Settings, check again, and only then disable it.
  - Never disable the `FallbackHome` activities.
  - Done means `dumpsys activity activities | grep mFocusedApp` names `tv.samurai.home` after pressing HOME. Not "set-home-activity said Success".
- On Android 9–10 the Wi-Fi button needs **Location** turned on (Settings > Location, or the TV's privacy settings) to show the network name; otherwise it just shows "Wi-Fi". Tell me if that applies.
- After taking over HOME, ask me to press the remote's dedicated buttons (Input, app buttons, Settings) and tell you which ones no longer work, and note them in `state/choices.md`.

## 6. Verify with screenshots

Take and **look at** each screenshot with `tools/shot.sh`; fix what you see before moving on:

- **Homescreen at rest**: clock millisecond ticker, date with ISO week number, live Wi-Fi signal indicator, Night City Weather with Cyberpunk environmental hazard telemetry, CPU and RAM circular gauges, Now Playing Samurai audio deck with live equalizer bounce, and 6 Data Shards in my requested order.
- **D-pad Navigation & Widgets**:
  - Focus and click System Status to execute **`[ PURGE RAM ]`**; verify background tasks are swept and memory telemetry reduces.
  - Focus and click Weather to open the **City Telemetry dialog**; verify preset list and custom satellite city search.
  - Focus and click Now Playing **`[ SELECT MEDIA / USB ]`**; verify audio track selection and continuous playback animation.
  - D-pad Down into the **Secondary App Matrix** (expanded 6-shards-per-row grid); verify all installed apps are reachable.
  - Hold OK / Menu on an app shard; verify the Shard Control Menu and custom Cyberpunk color palette.
- Live TV and HDMI screenshots come back black; check `topResumedActivity` instead.
- Check for crashes: `logcat -d | grep -iE "FATAL|AndroidRuntime"` filtered to `tv.samurai.home`.

## 7. Finish

- Complete `state/choices.md`: model, connection type (USB or Wi-Fi), choices made, disabled list by category, firmware re-enables, remote button behavior, and screenshots taken.
- Explain in two lines: `tools/restore.sh` undoes everything (or one app), and after a big firmware update `tools/reapply.sh` puts it all back. Keep automatic updates on.
- **Close ADB.** Nothing should be left able to control the TV; over Wi-Fi port 5555 is unencrypted and must not stay open. Ask me to, on the TV:
  1. Developer options > **Revoke USB debugging authorisations**.
  2. Turn **USB debugging** and **Network debugging** off (whichever are on).
  3. Restart the TV.

  Then verify, depending on how we connected:
  - **Wi-Fi:** run `python3 tools/adb-closed.py <ip>` (on Windows `py -3 tools\adb-closed.py <ip>`). It must print `CLOSED (connection refused)`. A timeout or "no route to host" proves nothing; ask me to check the TV is on and try again.
  - **USB:** with the cable **still plugged in**, run `python3 tools/adb-closed.py --usb` (Windows: `py -3 tools\adb-closed.py --usb`). It must print `CLOSED`. Then I can unplug the cable. If Network debugging was ever on, or the TV's IP is known, also run the Wi-Fi check above.


