"""AndroidOnly: WP-003 Bounded WP-207 runtime evidence input regressions."""

from pathlib import Path
import tempfile
import unittest

from collect_native_evidence import artifact_record
from controller.errors import PortError


class RuntimeArtifactRecordTests(unittest.TestCase):
    def test_records_only_regular_files_inside_the_declared_root(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "reports" / "TEST-runtime.xml"
            report.parent.mkdir()
            report.write_bytes(b"<testsuite/>")
            self.assertEqual(
                {
                    "path": "reports/TEST-runtime.xml",
                    "size": 12,
                    "sha256": "55a2c4dabbdd641e56e0ce28262e1d43b8fff7534ced8580f39ba573eca56f2c",
                },
                artifact_record(root, report),
            )
            outside = root.parent / "outside-runtime-report.xml"
            outside.write_bytes(b"<testsuite/>")
            try:
                with self.assertRaisesRegex(PortError, "escapes"):
                    artifact_record(root, outside)
            finally:
                outside.unlink()


if __name__ == "__main__":
    unittest.main()
