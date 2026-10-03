"""AndroidOnly: WP-003 Committed-tree delivery, not merely a populated developer filesystem."""

import copy
import unittest
from unittest.mock import patch

from fixtures import REPO
from controller.errors import PortError
from controller.model import git, tree
from controller.runtime_inputs import required_inputs, verify_committed_inputs, verify_tree_entries


class RuntimeInputTests(unittest.TestCase):
    def test_every_required_input_exists_in_the_exact_committed_head(self):
        result = verify_committed_inputs(REPO)
        self.assertEqual(result["result"], "valid")
        self.assertTrue(result["checkout_matches_committed_inputs"])
        self.assertIn("tools/android-port/controller/toolchain-pins.json", required_inputs())

    def test_missing_pins_requirements_or_reader_cannot_be_hidden_by_local_files(self):
        head = git(REPO, "rev-parse", "HEAD").decode().strip()
        entries = tree(REPO, head)
        for path in ("tools/android-port/controller/toolchain-pins.json",
                     "tools/android-port/controller/requirements-ci.txt",
                     "tools/android-port/controller/ci_evidence.py"):
            changed = copy.deepcopy(entries)
            changed.pop(path, None)
            with self.subTest(path=path), self.assertRaisesRegex(PortError, "committed Git tree"):
                verify_tree_entries(changed)

    def test_checkout_drift_or_uncommitted_replacement_is_not_committed_delivery(self):
        from pathlib import Path

        original = Path.read_bytes

        def read(path):
            return b"uncommitted fixture replacement" if path.name == "toolchain-pins.json" else original(path)

        with patch.object(Path, "read_bytes", read), self.assertRaisesRegex(PortError, "differs"):
            verify_committed_inputs(REPO)
