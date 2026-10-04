"""Android-only WP-002 fail-closed candidate environment assertions; no host credential values used."""

import os
import unittest
from unittest.mock import patch

from check_environment import allowed_environment_names, check_environment


class EnvironmentIsolationTests(unittest.TestCase):
    def test_toolchain_and_private_cache_names_are_allowed(self):
        allowed = allowed_environment_names()
        self.assertTrue({"java_home", "android_home", "android_user_home", "gradle_user_home"} <= allowed)

    def test_known_and_unknown_secret_names_are_not_allowed(self):
        allowed = allowed_environment_names()
        for name in ("GH_TOKEN", "COPILOT_TOKEN", "SPEECH_KEY", "CUSTOM_AUTHENTICATION"):
            self.assertNotIn(name.casefold(), allowed)

    def test_inherited_secret_is_rejected_without_echoing_its_value(self):
        with patch.dict(os.environ, {"SPEECH_KEY": "test-only-not-a-credential"}, clear=True):
            with self.assertRaises(ValueError) as failure:
                check_environment()
        self.assertIn("SPEECH_KEY", str(failure.exception))
        self.assertNotIn("test-only-not-a-credential", str(failure.exception))

    def test_missing_required_environment_cannot_return_ready(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "JAVA_HOME"):
                check_environment()


if __name__ == "__main__":
    unittest.main()
