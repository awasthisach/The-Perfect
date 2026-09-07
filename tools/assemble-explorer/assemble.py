#!/usr/bin/env python3
import base64
from pathlib import Path

ROOT = Path("tools/assemble-explorer")
TARGETS = {
    "ExplorerViewModel": "feature/explorer/src/main/java/com/vvf/smartmanager/feature/explorer/ExplorerViewModel.kt",
    "ExplorerDialogs": "feature/explorer/src/main/java/com/vvf/smartmanager/feature/explorer/components/ExplorerDialogs.kt",
    "ExplorerScreen": "feature/explorer/src/main/java/com/vvf/smartmanager/feature/explorer/ExplorerScreen.kt",
}
for key, dest in TARGETS.items():
    parts = sorted((ROOT / key).glob("*.b64"))
    data = base64.b64decode("".join(p.read_text().strip() for p in parts))
    Path(dest).write_bytes(data)
    print("wrote", dest, len(data))
