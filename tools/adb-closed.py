"""Check that ADB on the TV is really closed.

Usage: python3 tools/adb-closed.py <tv ip> [port]      (Windows: py -3 tools\\adb-closed.py <tv ip>)
       python3 tools/adb-closed.py --usb [serial]

Wi-Fi: only "connection refused" proves the port is closed. A timeout or "no route to host" can also
mean a firewall, a sleeping TV or macOS Local Network privacy blocking the check, so it proves nothing.

USB: run it with the cable still plugged in, after turning USB debugging off. It proves nothing with the
cable unplugged. With a serial, only that device is checked; without one, any USB device that adb can
see counts as open. If Network debugging was ever on, check the Wi-Fi port too.
"""
import os
import shutil
import socket
import subprocess
import sys

if len(sys.argv) < 2:
    print(__doc__.strip())
    sys.exit(2)


def find_adb():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    for name in ("adb.exe", "adb"):
        p = os.path.join(root, "platform-tools", name)
        if os.path.isfile(p):
            return p
    return shutil.which("adb")


def check_usb(serial):
    adb = find_adb()
    if not adb:
        print("UNKNOWN: adb not found (platform-tools/ in the repo or on PATH).")
        sys.exit(2)
    try:
        out = subprocess.run([adb, "devices"], capture_output=True, text=True, timeout=20).stdout
    except (OSError, subprocess.SubprocessError) as e:
        print(f"UNKNOWN ({e.__class__.__name__}: {e}): could not run adb devices.")
        sys.exit(2)
    usb = []
    for line in out.splitlines()[1:]:
        parts = line.strip().split("\t")
        if len(parts) < 2:
            continue
        s, state = parts[0], parts[1]
        # Network, wireless-debugging (mDNS) and emulator entries are not USB.
        if ":" in s or "._adb-tls" in s or s.startswith("emulator-"):
            continue
        if serial and s != serial:
            continue
        usb.append((s, state))
    if usb:
        listed = ", ".join(f"{s} ({state})" for s, state in usb)
        print(f"OPEN: adb still sees {listed} over USB. Turn USB debugging off on the TV.")
        sys.exit(1)
    print("CLOSED (no USB device visible to adb). Valid only with the cable still plugged in.")


if sys.argv[1] == "--usb":
    check_usb(sys.argv[2] if len(sys.argv) > 2 else None)
    sys.exit(0)

host = sys.argv[1]
port = int(sys.argv[2]) if len(sys.argv) > 2 else 5555
s = socket.socket()
s.settimeout(4)
try:
    s.connect((host, port))
    print(f"OPEN: {host}:{port} accepts connections. ADB is still reachable.")
    sys.exit(1)
except ConnectionRefusedError:
    print(f"CLOSED (connection refused): {host}:{port}")
except OSError as e:
    print(f"UNKNOWN ({e.__class__.__name__}: {e}): the TV may be off or unreachable. This does not prove the port is closed.")
    sys.exit(2)
finally:
    s.close()
