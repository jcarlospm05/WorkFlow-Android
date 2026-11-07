"""Expose synthetic emulator failure evidence through CI logs for remote diagnosis."""
import base64
from pathlib import Path
import sys

roots = [Path(arg) for arg in sys.argv[1:]]
for root in roots:
    for path in root.rglob("logcat-*.txt"):
        for line in path.read_text(errors="replace").splitlines():
            if "System.out" in line:
                print(line)
    for path in root.rglob("failure-*.png"):
        print("DIALOG_IMAGE_BEGIN " + path.name)
        encoded = base64.b64encode(path.read_bytes()).decode("ascii")
        for start in range(0, len(encoded), 16000):
            print("DIALOG_IMAGE_CHUNK " + encoded[start:start + 16000])
        print("DIALOG_IMAGE_END")
