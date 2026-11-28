import copy
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).with_name("verify_deployment_preflight.py")
SPEC = importlib.util.spec_from_file_location("verify_deployment_preflight", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
ROOT = Path(__file__).resolve().parents[2]


def protected_environment():
    return {"name": "production", "can_admins_bypass": False,
            "deployment_branch_policy": {"protected_branches": True, "custom_branch_policies": False},
            "protection_rules": [{"type": "required_reviewers", "prevent_self_review": True,
                                  "reviewers": [{"type": "User", "reviewer": {"id": 123}}]}]}


def approved_config():
    return {"vars": {"GEMINI_PRODUCTION_APPROVED": "true", "EMBEDDING_MODEL": "gemini-embedding-2",
                     "EMBEDDING_VERSION": "4", "EMBEDDING_DIMENSION": "768",
                     "FIREBASE_PROJECT_ID": "test-project", "FIREBASE_PROJECT_NUMBER": "123"}}


class DeploymentPreflightTests(unittest.TestCase):
    def test_accepts_explicit_review_and_branch_protections(self):
        MODULE.verify_environment(protected_environment(), "production")

    def test_rejects_missing_or_wrong_environment(self):
        for value in [{}, {"name": "release"}, None]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                MODULE.verify_environment(value, "production")

    def test_rejects_absent_reviewers(self):
        value = protected_environment(); value["protection_rules"] = []
        with self.assertRaises(ValueError): MODULE.verify_environment(value, "production")

    def test_rejects_self_review(self):
        value = protected_environment(); value["protection_rules"][0]["prevent_self_review"] = False
        with self.assertRaises(ValueError): MODULE.verify_environment(value, "production")

    def test_rejects_explicit_admin_bypass(self):
        value = protected_environment(); value["can_admins_bypass"] = True
        with self.assertRaises(ValueError): MODULE.verify_environment(value, "production")

    def test_rejects_unrestricted_branches(self):
        value = protected_environment(); value["deployment_branch_policy"] = None
        with self.assertRaises(ValueError): MODULE.verify_environment(value, "production")

    def test_accepts_reviewed_worker_configuration(self):
        MODULE.verify_worker_config(approved_config())

    def test_rejects_unapproved_provider_before_deployment(self):
        value = approved_config(); value["vars"]["GEMINI_PRODUCTION_APPROVED"] = "false"
        with self.assertRaises(ValueError): MODULE.verify_worker_config(value)

    def test_rejects_wrong_model_contract(self):
        value = approved_config(); value["vars"]["EMBEDDING_VERSION"] = "3"
        with self.assertRaises(ValueError): MODULE.verify_worker_config(value)

    def test_workflows_preflight_before_remote_mutations(self):
        deploy = (ROOT / ".github/workflows/deploy-embedding-worker.yml").read_text()
        release = (ROOT / ".github/workflows/release.yml").read_text()
        self.assertLess(deploy.index("verify_deployment_preflight.py"), deploy.index("wrangler secret put"))
        self.assertLess(deploy.index("verify_deployment_preflight.py"), deploy.index("wrangler deploy --config"))
        self.assertLess(release.index("verify_deployment_preflight.py"), release.index("Materialize signing and Firebase"))
        self.assertIn("--environment production", deploy)
        self.assertIn("--worker-config cloudflare/drive-semantic-embed/wrangler.toml", deploy)
        self.assertIn("--environment release", release)


if __name__ == "__main__": unittest.main()
