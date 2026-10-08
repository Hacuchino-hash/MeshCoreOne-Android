"""AndroidOnly: WP-003 Installed hook parsing, transport and runtime-copy regressions."""

import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

try:
    import check
except ImportError:
    repo = Path(__file__).resolve().parents[3]
    common = Path(subprocess.check_output(
        ["git", "-C", str(repo), "rev-parse", "--path-format=absolute", "--git-common-dir"],
        text=True,
    ).strip())
    sys.path.insert(0, str(common / "hooks" / "meshcore-local"))
    import check

RESERVATIONS = Path(check.__file__).resolve().parents[2] / "meshcore-reservations"
if (Path(check.__file__).parent.name != "meshcore-local"
        or Path(check.__file__).parent.parent.name != "hooks"
        or not (RESERVATIONS / "reserve.py").is_file()):
    RESERVATIONS = Path.cwd() / ".git" / "meshcore-local-reservations"
if not (RESERVATIONS / "reserve.py").is_file():
    raise RuntimeError("Missing exact reservation runtime; hook regressions cannot be skipped")
sys.dont_write_bytecode = True
sys.path.insert(0, str(RESERVATIONS))
import reserve
from controller.ledger import Ledger


class LocalCheckTests(unittest.TestCase):
    def test_multiple_exact_push_tips_not_head(self):
        first, second = "a" * 40, "b" * 40
        text = (
            f"refs/heads/a {first} refs/heads/a {check.ZERO}\n"
            f"refs/heads/b {second} refs/heads/b {first}\n"
        )
        self.assertEqual(check.push_commits(text), [first, second])

    def test_deletions_duplicates_and_empty_push(self):
        sha = "a" * 40
        deletion = f"(delete) {check.ZERO} refs/heads/deleted {sha}\n"
        update = f"refs/heads/a {sha} refs/heads/a {check.ZERO}\n"
        self.assertEqual(check.push_commits(deletion + update + update), [sha])
        self.assertEqual(check.push_commits(""), [])

    def test_malformed_push_is_blocked(self):
        for text in (
            "bad",
            "refs/a bad refs/a " + check.ZERO,
            "refs/a " + "a" * 40 + " refs/a bad",
        ):
            with self.subTest(text=text), self.assertRaises(ValueError):
                check.push_commits(text)

    def test_unknown_empty_duplicate_stages_are_blocked(self):
        for stages in ("typo", "", "verify,verify", "verify,"):
            with self.subTest(stages=stages), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(check.main(["--stages", stages]), 1)

    def test_partial_or_worktree_pre_push_is_blocked(self):
        for options in (["--stages", "verify"], ["--worktree"], ["--install-hook"]):
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(check.main(["--pre-push", *options]), 1)

    def test_nonzero_runner_blocks_push(self):
        with patch("check.capture", return_value=str(Path.cwd())), \
             patch("check.transport", return_value=[]), \
             patch("check.run_candidate",
                   side_effect=subprocess.CalledProcessError(1, ["checks"])), \
             contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(check.main([]), 1)

    def test_hook_runs_every_exact_commit(self):
        first, second = "a" * 40, "b" * 40
        text = f"refs/a {first} {first} {check.ZERO}\nrefs/b {second} {second} {check.ZERO}\n"
        with patch("sys.stdin", io.StringIO(text)), \
             patch("check.capture", return_value=str(Path.cwd())), \
             patch("check.transport", return_value=[]), \
             patch("check.run_candidate") as run:
            self.assertEqual(check.main(["--pre-push"]), 0)
            self.assertEqual([call.args[1] for call in run.call_args_list], [first, second])

    def test_missing_or_broken_wsl_does_not_install(self):
        with patch("check.os.name", "nt"), patch("check.shutil.which", return_value=None):
            with self.assertRaisesRegex(ValueError, "WSL is unavailable"):
                check.transport(None)
        with patch("check.os.name", "nt"), patch("check.shutil.which", return_value="wsl.exe"), \
             patch("check.subprocess.run",
                   side_effect=subprocess.CalledProcessError(1, ["wsl"])):
            with self.assertRaises(subprocess.CalledProcessError):
                check.transport("BrokenDistribution")

    def test_existing_hook_is_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            hooks = Path(temporary)
            hook = hooks / "pre-push"
            hook.write_text("#!/bin/sh\nexit 1\n", encoding="utf-8")
            with patch("check.capture", return_value=str(hooks)), \
                 patch("check.subprocess.run") as run:
                run.return_value.returncode = 1
                with self.assertRaisesRegex(ValueError, "Existing pre-push"):
                    check.install(hooks, ["wsl"], None)
            self.assertEqual(hook.read_text(encoding="utf-8"), "#!/bin/sh\nexit 1\n")


class InstalledRuntimeTests(unittest.TestCase):
    def test_relocated_runtime_is_exact_and_uses_advisory_schema(self):
        source_map = Path(reserve.__file__).with_name("source-sha256.json")
        if source_map.is_file():
            hashes = json.loads(source_map.read_text(encoding="utf-8"))
            for relative, expected in hashes.items():
                actual = Path(reserve.__file__).parent / relative
                self.assertEqual(hashlib.sha256(actual.read_bytes()).hexdigest(), expected)
        with tempfile.TemporaryDirectory() as temporary:
            ledger = Ledger(Path(temporary) / "ledger.sqlite3", "fixture/repository")
            with ledger.transaction() as connection:
                columns = {
                    row["name"] for row in connection.execute("PRAGMA table_info(leases)")
                }
            self.assertIn("record_state", columns)
            self.assertIn("capabilities", columns)


if __name__ == "__main__":
    unittest.main()
