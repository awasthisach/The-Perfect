#!/usr/bin/env python3
"""Restore explorer sources from known-good GitHub commits and apply surgical fixes."""
import re
import urllib.request
from pathlib import Path

BASE = "https://raw.githubusercontent.com/awasthisach/The-Perfect"

# VM + Dialogs from last good storage commit; Screen from main
SOURCES = {
    "feature/explorer/src/main/java/com/vvf/smartmanager/feature/explorer/ExplorerViewModel.kt":
        f"{BASE}/02694bbcdf37a540c6b64b2b8244674b8330971f/feature/explorer/src/main/java/com/vvf/smartmanager/feature/explorer/ExplorerViewModel.kt",
    "feature/explorer/src/main/java/com/vvf/smartmanager/feature/explorer/components/ExplorerDialogs.kt":
        f"{BASE}/02694bbcdf37a540c6b64b2b8244674b8330971f/feature/explorer/src/main/java/com/vvf/smartmanager/feature/explorer/components/ExplorerDialogs.kt",
    "feature/explorer/src/main/java/com/vvf/smartmanager/feature/explorer/ExplorerScreen.kt":
        f"{BASE}/main/feature/explorer/src/main/java/com/vvf/smartmanager/feature/explorer/ExplorerScreen.kt",
}

def fetch(url: str) -> str:
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read().decode("utf-8")

def fix_viewmodel(src: str) -> str:
    old = (
        '.onSuccess { _uiState.update { it.copy(userMessage = "Recycle bin emptied", '
        'trashFiles = emptyList()) ; loadStorageOverview() } }'
    )
    new = (
        '.onSuccess {\n'
        '                    _uiState.update { it.copy(userMessage = "Recycle bin emptied", '
        'trashFiles = emptyList()) }\n'
        '                    loadStorageOverview()\n'
        '                }'
    )
    if old in src:
        src = src.replace(old, new, 1)
        print("patched emptyTrash")
    return src

def fix_dialogs(src: str) -> str:
    # wrong import
    src = src.replace(
        "import androidx.compose.ui.Modifier.Modifier\n",
        "import androidx.compose.ui.Modifier\n",
    )
    # dedupe Modifier import
    lines, seen = [], False
    for line in src.splitlines(True):
        if line.strip() == "import androidx.compose.ui.Modifier":
            if seen:
                continue
            seen = True
        if line.strip() == "import androidx.compose.ui.Modifier.Modifier":
            continue
        lines.append(line)
    src = "".join(lines)
    if "import androidx.compose.ui.Modifier\n" not in src:
        src = src.replace(
            "import androidx.compose.ui.Alignment\n",
            "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\n",
        )
    print("patched Dialogs Modifier import")
    return src

def fix_screen(src: str) -> str:
    if "OcrResultDialog" not in src:
        src = src.replace(
            "import com.vvf.smartmanager.feature.explorer.components.SyncToCloudDialog",
            "import com.vvf.smartmanager.feature.explorer.components.SyncToCloudDialog\n"
            "import com.vvf.smartmanager.feature.explorer.components.OcrResultDialog\n"
            "import android.content.ClipData\n"
            "import android.content.ClipboardManager",
        )
    old = (
        "        is ExplorerDialogState.FileDetails -> {\n"
        "            FileDetailsDialog(\n"
        "                file = dialog.file,\n"
        "                onDismiss = { viewModel.dismissDialog() }\n"
        "            )\n"
        "        }\n"
        "        is ExplorerDialogState.Progress -> {"
    )
    new = (
        "        is ExplorerDialogState.FileDetails -> {\n"
        "            FileDetailsDialog(\n"
        "                file = dialog.file,\n"
        "                onDismiss = { viewModel.dismissDialog() },\n"
        "                onOpen = {\n"
        "                    viewModel.dismissDialog()\n"
        "                    viewModel.navigateInto(dialog.file)\n"
        "                },\n"
        "                onOcr = {\n"
        "                    viewModel.requestOcr(dialog.file)\n"
        "                }\n"
        "            )\n"
        "        }\n"
        "        is ExplorerDialogState.OcrInProgress -> {\n"
        "            androidx.compose.material3.AlertDialog(\n"
        "                onDismissRequest = {},\n"
        "                title = { Text(\"Scanning OCR\u2026\") },\n"
        "                text = { Text(\"Extracting English + Hindi text\u2026\") },\n"
        "                confirmButton = {}\n"
        "            )\n"
        "        }\n"
        "        is ExplorerDialogState.OcrResult -> {\n"
        "            OcrResultDialog(\n"
        "                fileName = dialog.fileName,\n"
        "                text = dialog.text,\n"
        "                onDismiss = { viewModel.dismissDialog() },\n"
        "                onCopy = {\n"
        "                    val cm = context.getSystemService(ClipboardManager::class.java)\n"
        "                    cm?.setPrimaryClip(ClipData.newPlainText(\"OCR\", dialog.text))\n"
        "                }\n"
        "            )\n"
        "        }\n"
        "        is ExplorerDialogState.Progress -> {"
    )
    if old not in src:
        raise SystemExit("FileDetails block not found in ExplorerScreen")
    src = src.replace(old, new, 1)
    print("patched Screen OCR/Open wiring")
    return src

def main() -> None:
    for dest, url in SOURCES.items():
        print("fetch", url)
        text = fetch(url)
        if dest.endswith("ExplorerViewModel.kt"):
            text = fix_viewmodel(text)
        elif dest.endswith("ExplorerDialogs.kt"):
            text = fix_dialogs(text)
        elif dest.endswith("ExplorerScreen.kt"):
            text = fix_screen(text)
        path = Path(dest)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        print("wrote", dest, len(text))

if __name__ == "__main__":
    main()
