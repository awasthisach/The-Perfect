import re
import unittest
from pathlib import Path
import yaml

HASH_LENGTHS = {"MD5": 32, "SHA-1": 40, "SHA-256": 64, "SHA-384": 96, "SHA-512": 128}


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
        config = yaml.safe_load((Path(__file__).resolve().parents[2] / ".ort.yml").read_text())
        count = 0
        for package in config.get("curations", {}).get("packages", []):
            artifact = package.get("curations", {}).get("source_artifact")
            if not artifact:
                continue
            count += 1
            value = artifact.get("hash", {}).get("value")
            algorithm = artifact.get("hash", {}).get("algorithm")
            self.assertTrue(valid_hash(algorithm, value), f"{package['id']}: invalid {algorithm}; length={len(value or '')}")
        self.assertGreaterEqual(count, 6)

    def test_artifact_origin_requires_a_pinned_matching_source(self):
        config = yaml.safe_load((Path(__file__).resolve().parents[2] / ".ort.yml").read_text())
        for package in config.get("curations", {}).get("packages", []):
            curation = package.get("curations", {})
            if "ARTIFACT" not in curation.get("source_code_origins", []):
                continue
            artifact = curation.get("source_artifact", {})
            provider, group, name, version = package["id"].split(":")
            self.assertEqual("Maven", provider)
            expected = f"https://repo.maven.apache.org/maven2/{group.replace('.', '/')}/{name}/{version}/{name}-{version}-sources.jar"
            self.assertEqual(expected, artifact.get("url"))
            self.assertTrue(valid_hash(artifact.get("hash", {}).get("algorithm"), artifact.get("hash", {}).get("value")))


if __name__ == "__main__":
    unittest.main()
