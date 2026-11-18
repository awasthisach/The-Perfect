import re
import unittest
from pathlib import Path

HASH_LENGTHS = {"MD5": 32, "SHA-1": 40, "SHA-256": 64, "SHA-384": 96, "SHA-512": 128}
CURATIONS_FILE = "ort-curations.yml"


def curation_blocks(text):
    """Return package id and curation text without requiring PyYAML in CI."""
    pattern = re.compile(
        r'^\s*- id: "(?P<id>[^"]+)"\s*\n(?P<body>.*?)(?=^\s*- id: "|\Z)',
        re.MULTILINE | re.DOTALL,
    )
    return [(match.group("id"), match.group("body")) for match in pattern.finditer(text)]


def valid_hash(algorithm, value):
    length = HASH_LENGTHS.get(algorithm)
    return bool(length and isinstance(value, str) and re.fullmatch(r"[0-9a-fA-F]{%d}" % length, value))


class OrtCurationHashTests(unittest.TestCase):
    def test_accepts_complete_sha1(self):
        self.assertTrue(valid_hash("SHA-1", "2efe18f7bea6fa9536802dd4ea54d948117216c6"))

    def test_rejects_truncated_sha1(self):
        self.assertFalse(valid_hash("SHA-1", "2efe18f7bea6fa9536802dd4ea54d948117216c"))

    def test_rejects_oversized_sha1(self):
        self.assertFalse(valid_hash("SHA-1", "a" * 41))

    def test_rejects_non_hex(self):
        self.assertFalse(valid_hash("SHA-1", "g" * 40))

    def test_rejects_missing_or_unknown_hash(self):
        self.assertFalse(valid_hash("SHA-1", None))
        self.assertFalse(valid_hash(None, "a" * 40))
        self.assertFalse(valid_hash("UNKNOWN", "a" * 40))

    def test_validates_sha256_length(self):
        self.assertTrue(valid_hash("SHA-256", "a" * 64))
        self.assertFalse(valid_hash("SHA-256", "a" * 63))

    def test_repository_source_artifact_hashes(self):
        text = (Path(__file__).resolve().parents[2] / CURATIONS_FILE).read_text()
        count = 0
        for package_id, body in curation_blocks(text):
            artifact = re.search(
                r"source_artifact:\s*\n\s+url:\s+\"(?P<url>[^\"]+)\"\s*\n"
                r"\s+hash:\s*\n\s+value:\s+\"(?P<value>[^\"]+)\"\s*\n"
                r"\s+algorithm:\s+\"(?P<algorithm>[^\"]+)\"",
                body,
            )
            if not artifact:
                continue
            count += 1
            value = artifact.group("value")
            algorithm = artifact.group("algorithm")
            self.assertTrue(valid_hash(algorithm, value), f"{package_id}: invalid {algorithm}; length={len(value or '')}")
        self.assertGreaterEqual(count, 6)

    def test_artifact_origin_requires_a_pinned_matching_source(self):
        text = (Path(__file__).resolve().parents[2] / CURATIONS_FILE).read_text()
        for package_id, body in curation_blocks(text):
            if "source_code_origins: [ARTIFACT]" not in body:
                continue
            artifact = re.search(r"source_artifact:.*?\n\s+url:\s+\"([^\"]+)\"", body, re.DOTALL)
            self.assertIsNotNone(artifact, package_id)
            provider, group, name, version = package_id.split(":")
            self.assertEqual("Maven", provider)
            expected = f"https://repo.maven.apache.org/maven2/{group.replace('.', '/')}/{name}/{version}/{name}-{version}-sources.jar"
            self.assertEqual(expected, artifact.group(1))
            artifact_body = re.search(r"source_artifact:(.*?)(?=\n\s+source_code_origins:)", body, re.DOTALL)
            self.assertIsNotNone(artifact_body, package_id)
            hash_match = re.search(
                r"value:\s+\"([^\"]+)\".*?algorithm:\s+\"([^\"]+)\"",
                artifact_body.group(1),
                re.DOTALL,
            )
            self.assertIsNotNone(hash_match, package_id)
            self.assertTrue(valid_hash(hash_match.group(2), hash_match.group(1)))


if __name__ == "__main__":
    unittest.main()
