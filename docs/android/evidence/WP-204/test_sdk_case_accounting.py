"""WP-204 fail-closed SDK-label accounting; fixture nodes are parser tests, never executed native evidence."""

import unittest
from collect_evidence import EvidenceFailure, account_native_profiles


class SdkCaseAccountingTest(unittest.TestCase):
    def setUp(self):
        self.methods = {("ActualSdkTest", "policy"): (31, 32, 33, 37), ("SingleTest", "case"): (31,)}
        self.cases = {
            ("ActualSdkTest", name): {"name": name} for name in ("policy", "policy[32]", "policy[33]", "policy[37]")
        }
        self.cases[("SingleTest", "case")] = {"name": "case"}

    def test_default_sdk_label_is_unambiguously_accounted(self):
        records = account_native_profiles(self.methods, self.cases)
        self.assertEqual([31, 32, 33, 37], [row["sdk"] for row in records])

    def test_all_explicit_sdk_labels_are_supported(self):
        self.cases[("ActualSdkTest", "policy[31]")] = self.cases.pop(("ActualSdkTest", "policy"))
        self.assertEqual(4, len(account_native_profiles(self.methods, self.cases)))

    def test_missing_profile_fails(self):
        self.cases.pop(("ActualSdkTest", "policy[37]"))
        with self.assertRaises(EvidenceFailure):
            account_native_profiles(self.methods, self.cases)

    def test_unreviewed_or_extra_profile_fails(self):
        for label in ("policy[34]", "policy[38]"):
            cases = dict(self.cases)
            cases[("ActualSdkTest", label)] = {"name": label}
            with self.assertRaises(EvidenceFailure):
                account_native_profiles(self.methods, cases)

    def test_unexpected_or_missing_native_method_fails(self):
        for cases in (
            {key: value for key, value in self.cases.items() if key != ("SingleTest", "case")},
            {**self.cases, ("SingleTest", "unexecuted-source-mismatch"): {"name": "extra"}},
        ):
            with self.assertRaises(EvidenceFailure):
                account_native_profiles(self.methods, cases)


if __name__ == "__main__":
    unittest.main()
