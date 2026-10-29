#!/usr/bin/env python3
"""Fetch only the trusted, checksum-pinned text fixture; never upload its bytes."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

manifest = json.loads(Path(sys.argv[2] if len(sys.argv) > 2 else "model-manifests/text-model.json").read_text())
destination = Path(sys.argv[1])
destination.parent.mkdir(parents=True, exist_ok=True)
subprocess.run([
    "curl", "--fail", "--location", "--silent", "--show-error", "--retry", "3",
    "--connect-timeout", "30", "--max-time", "1200", manifest["source"], "-o", str(destination)
], check=True)
digest = hashlib.sha256()
with destination.open("rb") as stream:
    for chunk in iter(lambda: stream.read(4 * 1024 * 1024), b""):
        digest.update(chunk)
if destination.stat().st_size != manifest["bytes"] or digest.hexdigest() != manifest["sha256"]:
    destination.unlink()
    raise SystemExit("Pinned model size/SHA-256 mismatch")
print("Verified pinned model:", manifest["file"], digest.hexdigest())
