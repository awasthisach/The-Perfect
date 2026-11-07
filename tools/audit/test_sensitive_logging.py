"""Static regression guards for reviewed sensitive file-processing log sites."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
PATHS = [
    "core/domain/src/main/java/com/vvf/smartmanager/core/domain/OcrIndexingService.kt",
    "plugins/plugin-ocr/src/main/java/com/vvf/smartmanager/plugin/ocr/OcrEnginePlugin.kt",
    "core/common/src/main/java/com/vvf/smartmanager/core/common/BitmapUtils.kt",
]


class SensitiveLoggingTests(unittest.TestCase):
    def test_file_processing_logs_do_not_include_identifiers_or_raw_errors(self):
        for path in PATHS:
            for line in (ROOT / path).read_text().splitlines():
                if "Log." not in line: continue
                with self.subTest(path=path, line=line):
                    self.assertNotRegex(line, r"(?:fileItem|event\.fileItem)\.(?:path|name)")
                    self.assertNotIn(".message}", line)
                    self.assertNotRegex(line, r",\s*(?:e|error|oom)\s*\)")

    def test_provider_file_error_does_not_propagate_raw_path(self):
        plugin = (ROOT / PATHS[1]).read_text()
        self.assertNotIn("File not found or unreadable: ${fileItem.path}", plugin)


if __name__ == "__main__": unittest.main()
