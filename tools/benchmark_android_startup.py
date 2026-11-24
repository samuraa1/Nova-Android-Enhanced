#!/usr/bin/env python3
"""Compare cold process starts for the two installed Nova builds through ADB."""

import argparse
import re
from statistics import median
import subprocess


PACKAGES = ("com.brent.nova", "com.brent.nova.enhanced")
MAIN_ACTIVITY = "com.example.nova.MainActivity"


def measure(adb: list[str], package: str) -> int:
    subprocess.run([*adb, "shell", "am", "force-stop", package], check=True)
    output = subprocess.check_output(
        [*adb, "shell", "am", "start", "-W", "-n", f"{package}/{MAIN_ACTIVITY}"],
        text=True,
    )
    total = re.search(r"^TotalTime: (\d+)\s*$", output, re.MULTILINE)
    if "Status: ok" not in output or total is None:
        raise RuntimeError(f"No valid startup measurement for {package}")
    return int(total.group(1))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial", help="ADB device ID for the dedicated test device")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--trials", type=int, default=5)
    args = parser.parse_args()
    if args.trials < 1:
        parser.error("--trials must be positive")

    adb = [args.adb, "-s", args.serial]
    trials = {package: [] for package in PACKAGES}
    for package in PACKAGES:
        subprocess.run([*adb, "shell", "pm", "path", package], check=True, capture_output=True)
    for trial in range(args.trials + 1):
        for package in PACKAGES:
            elapsed = measure(adb, package)
            if trial > 0:
                trials[package].append(elapsed)
            # Close the app's foreground screen before starting the other build.
            subprocess.run([*adb, "shell", "input", "keyevent", "KEYCODE_BACK"], check=True)

    for package, samples in trials.items():
        print(f"{package}: median={median(samples):g} ms, samples={samples}")


if __name__ == "__main__":
    main()
