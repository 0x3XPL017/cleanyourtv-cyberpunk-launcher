# Troubleshooting

Common issues and solutions for **Cyberpunk TV Launcher** on Android TV and Google TV.

---

## 1. Connection Issues

### TV does not appear in `adb devices` over USB
* **Data Cable**: Ensure your USB cable supports high-speed data transfer, not charging only.
* **USB Debugging**: Check that **Developer options > USB debugging** is enabled.
* **USB Port Capability**: Many TV sets have USB ports that are host-only (storage drives only) and lack USB debugging hardware pins. If your TV does not appear, switch to Wi-Fi.

### Wi-Fi Connection Refused (`port 5555`)
* Verify both your computer and the TV are connected to the same Wi-Fi network / subnet.
* In TV Developer options, ensure **Network debugging** (or **Wireless debugging**) is enabled.
* If connecting via IP fails, run `tools/connect.sh wifi <TV_IP_ADDRESS>`.

---

## 2. HOME Button & Default Launcher

### Pressing HOME returns to the stock launcher
Google TV occasionally ignores `cmd package set-home-activity`. To lock SamuraiOS as your default launcher:
1. Run `tools/build-install.sh`.
2. If the stock launcher keeps taking over, run:
   ```bash
   tools/disable.sh
   ```
   This safely disables the vendor stock launcher while keeping all your apps intact.
3. To revert back to the stock launcher at any time:
   ```bash
   tools/restore.sh
   ```

---

## 3. Memory Optimizer (RAM Purge)

### Background Apps on Android 14
* Android 14 restricts regular user apps from killing processes belonging to other applications.
* The launcher communicates locally with a privileged helper daemon (`tools/samurai_helper.sh`) running under `UID 2000 (shell)`.
* This daemon is automatically deployed and started whenever you run `tools/build-install.sh`.
* If you restart your TV standalone (without an ADB connection), clicking **`[ PURGE RAM ]`** gracefully cleans internal application caches and JVM heap. Reconnecting via `tools/connect.sh` or restarting the daemon re-enables deep system background task sweeps.

---

## 4. Reversibility & Restoring Stock State
Everything modified by this project is **100% reversible** without root:
```bash
tools/restore.sh
```
This restores your original stock TV launcher, re-enables all vendor packages, and reverts system settings.
