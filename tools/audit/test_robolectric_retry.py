from pathlib import Path
import tempfile
import unittest

from robolectric_retry import is_transient_robolectric_fetch, report_files


ROBOLECTRIC_FAILURE = (
    "Failed to fetch maven artifact org.robolectric:android-all-instrumented:13-robolectric"
)
HTTP_429 = (
    "java.io.IOException: Server returned HTTP response code: 429 "
    "for URL: https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented.jar"
)


class RobolectricRetryClassifierTest(unittest.TestCase):
    def test_detects_429_in_junit_xml_when_gradle_log_only_says_tests_failed(self):
        xml = f"""<testsuite><testcase><failure>{ROBOLECTRIC_FAILURE}
Caused by: {HTTP_429}</failure></testcase></testsuite>"""
        matched, source = is_transient_robolectric_fetch(
            "There were failing tests. See the report.", [("JUnit XML", xml)]
        )
        self.assertTrue(matched)
        self.assertEqual("JUnit XML", source)

    def test_detects_429_in_html_test_report(self):
        html = f"<pre>{ROBOLECTRIC_FAILURE}<br/>Caused by: {HTTP_429}</pre>"
        matched, source = is_transient_robolectric_fetch(
            "There were failing tests.", [("JUnit HTML", html)]
        )
        self.assertTrue(matched)
        self.assertEqual("JUnit HTML", source)

    def test_detects_transient_failure_in_live_log(self):
        log = f"{ROBOLECTRIC_FAILURE}\n{HTTP_429}"
        self.assertTrue(is_transient_robolectric_fetch(log, [])[0])

    def test_does_not_retry_permanent_http_404(self):
        report = f"{ROBOLECTRIC_FAILURE}\nHTTP response code: 404"
        self.assertFalse(is_transient_robolectric_fetch("", [("JUnit XML", report)])[0])

    def test_does_not_retry_assertion_or_compile_failure(self):
        report = "java.lang.AssertionError: expected true but was false"
        self.assertFalse(is_transient_robolectric_fetch("", [("JUnit XML", report)])[0])
        self.assertFalse(
            is_transient_robolectric_fetch(
                "Compilation failed: unresolved reference", []
            )[0]
        )

    def test_does_not_combine_unrelated_sources(self):
        matched, _ = is_transient_robolectric_fetch(
            ROBOLECTRIC_FAILURE,
            [("another test report", "HTTP response code: 429")],
        )
        self.assertFalse(matched)

    def test_detects_timeout_only_when_robolectric_artifact_fetch_is_present(self):
        report = f"{ROBOLECTRIC_FAILURE}\njava.net.SocketTimeoutException: Read timed out"
        self.assertTrue(is_transient_robolectric_fetch("", [("JUnit XML", report)])[0])
        self.assertFalse(
            is_transient_robolectric_fetch(
                "", [("JUnit XML", "java.net.SocketTimeoutException: Read timed out")]
            )[0]
        )

    def test_report_file_discovery_includes_xml_and_html(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            xml = root / "core/security/build/test-results/testDebugUnitTest/TEST-failure.xml"
            html = root / "core/security/build/reports/tests/testDebugUnitTest/classMethod.html"
            xml.parent.mkdir(parents=True)
            html.parent.mkdir(parents=True)
            xml.write_text("<testsuite/>", encoding="utf-8")
            html.write_text("<html/>", encoding="utf-8")
            found = set(report_files(root))
            self.assertIn(xml, found)
            self.assertIn(html, found)


if __name__ == "__main__":
    unittest.main()
