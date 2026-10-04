"""AndroidOnly: WP-004 Executed lexer, family, schema and frozen-source regressions."""

import copy
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from controller.errors import PortError
from controller.schema import decode_json
from oracle.reference import FrozenReference, OracleError, REPO, SOURCE_SHA, sha256, write_or_check
from oracle.swift import Syntax, canonical, declarations, lex
from test_inventory import generate, inventory_counts, parse_file, validate_outputs


def specimen(text='@Suite("S") struct S { @Test func works() { #expect(true) } }'):
    parsed = parse_file(text)
    original = {"path": "MC1Tests/SpecimenTests.swift", "blob_sha": "a" * 40,
                "kind": "test", "primary_owner": "WP-004"}
    file = {
        "path": original["path"], "source_sha": SOURCE_SHA, "blob_sha": original["blob_sha"],
        "utf8_bytes": len(text.encode("utf-8")), "sha256": sha256(text.encode("utf-8")),
        "kind": "test", "work_package": "WP-004", "exclusion": None, "role": "test-suite", **parsed,
    }
    catalog = {"schema_version": 1, "source_sha": SOURCE_SHA, "entries": [{
        "path": original["path"], "blob_sha": original["blob_sha"],
        "has_assertions": any(case["direct_assertions"] or case["helper_assertions"] for case in parsed["cases"]),
        "cases": [{"id": case["id"], "parameter_family": case["parameter_family"]} for case in parsed["cases"]],
    }]}
    details = {"schema_version": 1, "source_sha": SOURCE_SHA, "manifest_sha256": "b" * 64,
               "generator": "tools/android-port/test_inventory.py", "files": [file],
               "counts": inventory_counts([file])}
    return catalog, details, [original]


class SwiftLexicalTests(unittest.TestCase):
    def test_nested_comments_and_string_contents_do_not_register_tests(self):
        text = '''
        /* @Test func invented() { #expect(false) } /* nested */ */
        // @Test func alsoInvented() {}
        struct Real {
          let message = "@Test func invented() { #expect(false) }"
          let raw = ##"/* @Test */ \\#" quotation "##
          let multiline = """
          @Test func invented() {}
          """
          @Test func actual() { #expect(message.count > 0) }
        }
        '''
        parsed = parse_file(text)
        self.assertEqual([case["method"] for case in parsed["cases"]], ["actual"])
        self.assertEqual(len(parsed["file_assertions"]), 1)

    def test_nested_interpolation_strings_are_one_literal(self):
        text = r'let text = "value \(String("inner \(1 + 2)")) // @Test"; @Test func real() { #expect(true) }'
        self.assertEqual(sum(token.kind == "string" for token in lex(text)), 1)
        self.assertEqual(len(parse_file(text)["cases"]), 1)

    def test_raw_interpolation_and_escaped_delimiter(self):
        text = r'let x = #"hi \#("quoted") \#" still literal"#; @Test func real() { #expect(true) }'
        self.assertEqual(len(parse_file(text)["cases"]), 1)

    def test_escaped_names_display_names_and_line_numbers(self):
        case = parse_file('\n@Suite("Named") struct S {\n@Test("It \\"works\\"")\nfunc `has spaces`() { #expect(true) }\n}')["cases"][0]
        self.assertEqual(case["method"], "has spaces")
        self.assertEqual(case["display_name"], 'It "works"')
        self.assertEqual(case["line"], 4)
        self.assertEqual(case["annotations"][0]["line"], 3)
        self.assertEqual(case["port_status"], "pending")

    def test_raw_and_unicode_display_names_preserve_utf8(self):
        case = parse_file(r'@Test(#"CJK \u{4F60} quote "here""#) func t() { #expect(true) }')["cases"][0]
        self.assertEqual(case["display_name"], r'CJK \u{4F60} quote "here"')
        case = parse_file(r'@Test("\u{4F60}") func t() { #expect(true) }')["cases"][0]
        self.assertEqual(case["display_name"], "\u4f60")
        self.assertEqual(len(case["display_name"].encode("utf-8")), 3)

    def test_unterminated_comment_string_and_delimiters_fail(self):
        for text in ('/* missing', 'let x = "missing', 'struct S { @Test func t() { #expect(true) }'):
            with self.subTest(text=text), self.assertRaises(OracleError):
                parse_file(text)

    def test_operator_array_extension_and_deinitializer_are_support(self):
        parsed = parse_file('''
        struct Instant { static func <(a: Instant, b: Instant) -> Bool { false } }
        extension [Int] { var sum: Int { reduce(0, +) } }
        class Box { deinit {} }
        ''')
        self.assertEqual(parsed["cases"], [])
        self.assertEqual([method["method"] for method in parsed["non_test_methods"]], ["<", "deinit"])

    def test_xctest_instance_methods_not_helpers_or_static_methods(self):
        parsed = parse_file('''
        class Tests: XCTestCase {
          func testReal() throws { XCTAssertEqual(1, 1) }
          func testWithArgument(_ value: Int) { XCTAssertTrue(true) }
          static func testStatic() { XCTAssertTrue(true) }
          func helper() { XCTAssertTrue(true) }
        }
        struct NotXCTest { func testHelper() { #expect(true) } }
        ''')
        self.assertEqual([case["method"] for case in parsed["cases"]], ["testReal"])
        self.assertEqual(parsed["cases"][0]["framework"], "XCTest")
        self.assertEqual(len(parsed["non_test_methods"]), 4)

    def test_helper_assertions_are_separate_from_direct_assertions(self):
        parsed = parse_file('''
        struct S {
          @Test func real() { checkHelper() }
          func checkHelper() { anotherHelper() }
          func anotherHelper() { #expect(true) }
        }
        ''')
        case = parsed["cases"][0]
        self.assertEqual(case["direct_assertions"], [])
        self.assertEqual(case["assertion_status"], "helper")
        self.assertEqual(case["helper_assertions"][0]["method"], "anotherHelper")
        self.assertEqual(len(parsed["non_test_methods"]), 2)

    def test_throwing_eventual_assertions_retain_independent_helper_provenance(self):
        helper = {
            "method": "waitUntil", "line": 4,
            "assertions": [{"kind": "eventual-condition/WaitTimeoutError", "line": 18}],
            "provenance": {"path": "MC1Tests/Helpers/TestPolling.swift", "source_sha": SOURCE_SHA,
                           "blob_sha": "a" * 40, "utf8_bytes": 100, "sha256": "b" * 64},
        }
        for call in ('waitUntil { true }', 'waitUntil("expected event") { true }'):
            parsed = parse_file(f'@Test func eventual() async throws {{ try await {call} }}',
                                external_helpers={"waitUntil": helper})
            with self.subTest(call=call):
                self.assertEqual(parsed["cases"][0]["assertion_status"], "helper")
                self.assertEqual(parsed["cases"][0]["helper_assertions"][0]["provenance"]["blob_sha"], "a" * 40)

    def test_swallowed_polling_failure_is_not_an_assertion_claim(self):
        helper = {"method": "waitUntil", "line": 1, "assertions": [{"kind": "eventual-condition", "line": 2}],
                  "provenance": {"path": "Pinned.swift", "source_sha": SOURCE_SHA}}
        case = parse_file('@Test func swallowed() async { try? await waitUntil("ignored") { false } }',
                          external_helpers={"waitUntil": helper})["cases"][0]
        self.assertEqual(case["assertion_status"], "none")

    def test_no_assertion_tests_remain_pending_and_identifiable(self):
        case = parse_file('@Test func noAssertions() { let value = 1 }')["cases"][0]
        self.assertEqual(case["assertion_status"], "none")
        self.assertEqual(case["port_status"], "pending")

    def test_orphan_and_nested_test_annotations_fail_explicitly(self):
        for text in ('@Test var value = 1', '@Test struct Bad {}', '@Test',
                     'func helper() { @Test func nested() { #expect(true) } }'):
            with self.subTest(text=text), self.assertRaises(OracleError):
                parse_file(text)

    def test_conditional_cases_retain_each_branch(self):
        parsed = parse_file('''
        #if os(iOS)
        @Test func apple() { #expect(true) }
        #else
        @Test func other() { #expect(true) }
        #endif
        ''')
        self.assertEqual(parsed["cases"][0]["compile_conditions"], [["# if os ( iOS )"]])
        self.assertEqual(parsed["cases"][1]["compile_conditions"], [["# if os ( iOS )", "# else"]])


class ParameterFamilyTests(unittest.TestCase):
    def test_literal_tuples_and_struct_inputs_keep_real_declared_rows(self):
        case = parse_file('''
        @Test("Pairs", .serialized, arguments: [
          (1, "a,b"), /* no invented row */ (2, "c"),
          Row(flag: true, text: #"\\quoted"#),
        ])
        func pairs(_ row: Row) { #expect(true) }
        ''')["cases"][0]
        self.assertEqual(case["inputs"]["declared_count"], 3)
        self.assertEqual(case["inputs"]["axes"][0]["declared_rows"][0], '( 1 , "a,b" )')
        self.assertIn("Row ( flag :", case["inputs"]["axes"][0]["declared_rows"][2])
        self.assertNotEqual(case["parameter_family"], "single")

    def test_cartesian_axes_are_not_counted_as_two_unparameterized_tests(self):
        case = parse_file('@Test(arguments: [1, 2], [true, false, true]) func t(_ i: Int, _ flag: Bool) { #expect(true) }')["cases"][0]
        self.assertEqual(case["inputs"]["combination"], "cartesian")
        self.assertEqual(case["inputs"]["declared_count"], 6)
        self.assertEqual(len(case["inputs"]["axes"]), 2)

    def test_named_collection_resolves_its_actual_declaration(self):
        case = parse_file('''
        struct S {
          @Test(arguments: rows) func t(_ value: Int) { #expect(value > 0) }
          nonisolated static let rows: [Int] = [1, 2, 3]
        }
        ''')["cases"][0]
        axis = case["inputs"]["axes"][0]
        self.assertEqual(axis["kind"], "named-collection")
        self.assertEqual(axis["declared_rows"], ["1", "2", "3"])
        self.assertEqual(axis["definition"]["symbol"], "rows")
        self.assertIn("[1, 2, 3]", axis["definition"]["declaration"])

    def test_enum_family_retains_blob_and_filter_predicate(self):
        dependency = {"path": "Pinned.swift", "source_sha": SOURCE_SHA, "blob_sha": "a" * 40,
                      "utf8_bytes": 32, "sha256": "b" * 64, "symbol": "Kind", "line": 1,
                      "cases": ["one", "two", "custom"]}
        case = parse_file('@Test(arguments: Kind.allCases.filter { $0 != .custom }) func t(_ kind: Kind) { #expect(true) }',
                          dependencies={"Kind": dependency})["cases"][0]
        axis = case["inputs"]["axes"][0]
        self.assertEqual(axis["declared_rows"], ["one", "two"])
        self.assertEqual(axis["definition"]["blob_sha"], "a" * 40)
        self.assertIn("filter", axis["expression"])

    def test_unknown_empty_and_unsupported_input_families_fail(self):
        for expression in ("[]", "missing", "Other.allCases", "rows.map { $0 }"):
            with self.subTest(expression=expression), self.assertRaises(OracleError):
                parse_file(f'@Test(arguments: {expression}) func t(_ value: Int) {{ #expect(true) }}')

    def test_comment_and_whitespace_changes_do_not_change_family_id(self):
        one = parse_file('@Test(arguments: [1, 2]) func t(_ n: Int) { #expect(true) }')["cases"][0]
        two = parse_file('@Test(arguments: [1, /* documented */\n2]) func t(_ n: Int) { #expect(true) }')["cases"][0]
        self.assertEqual(one["parameter_family"], two["parameter_family"])

    def test_changed_input_or_method_rename_changes_identity(self):
        one = parse_file('@Test(arguments: [1, 2]) func t(_ n: Int) { #expect(true) }')["cases"][0]
        changed = parse_file('@Test(arguments: [1, 3]) func t(_ n: Int) { #expect(true) }')["cases"][0]
        renamed = parse_file('@Test(arguments: [1, 2]) func renamed(_ n: Int) { #expect(true) }')["cases"][0]
        self.assertNotEqual(one["parameter_family"], changed["parameter_family"])
        self.assertNotEqual(one["id"], renamed["id"])


class InventorySchemaTests(unittest.TestCase):
    def test_controller_catalog_shape_is_unchanged(self):
        catalog, details, originals = specimen()
        validate_outputs(catalog, details, originals)
        self.assertEqual(set(catalog), {"schema_version", "source_sha", "entries"})
        self.assertEqual(set(catalog["entries"][0]["cases"][0]), {"id", "parameter_family"})

    def test_duplicate_case_and_path_fail(self):
        for kind in ("case", "path"):
            catalog, details, originals = specimen()
            if kind == "case":
                catalog["entries"][0]["cases"] *= 2
            else:
                catalog["entries"] *= 2
            with self.subTest(kind=kind), self.assertRaises(OracleError):
                validate_outputs(catalog, details, originals)

    def test_missing_renamed_unknown_and_stale_paths_fail(self):
        for kind in ("missing", "renamed", "blob", "source"):
            catalog, details, originals = specimen()
            if kind == "missing":
                catalog["entries"] = []
            elif kind == "renamed":
                catalog["entries"][0]["path"] = "MC1Tests/RenamedTests.swift"
            elif kind == "blob":
                catalog["entries"][0]["blob_sha"] = "f" * 40
            else:
                catalog["source_sha"] = "f" * 40
            with self.subTest(kind=kind), self.assertRaises(OracleError):
                validate_outputs(catalog, details, originals)

    def test_zero_tests_are_not_reclassified_as_support(self):
        catalog, details, originals = specimen("struct Empty {}")
        with self.assertRaisesRegex(OracleError, "Zero-test"):
            validate_outputs(catalog, details, originals)

    def test_detail_case_schema_counts_and_claimed_pass_fail(self):
        for kind in ("schema", "count", "pass", "family"):
            catalog, details, originals = specimen('@Test(arguments: [1, 2]) func t(_ n: Int) { #expect(true) }')
            case = details["files"][0]["cases"][0]
            if kind == "schema":
                case["invented"] = True
            elif kind == "count":
                details["counts"]["case_declarations"] += 1
            elif kind == "pass":
                case["port_status"] = "PASS"
            else:
                case["inputs"]["axes"][0]["declared_count"] = 3
            with self.subTest(kind=kind), self.assertRaises(OracleError):
                validate_outputs(catalog, details, originals)

    def test_duplicate_json_keys_are_rejected(self):
        with self.assertRaises(PortError):
            decode_json('{"schema_version":1,"schema_version":2}')

    def test_check_never_regenerates_a_missing_or_stale_output(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "output.json"
            with self.assertRaises(OracleError):
                write_or_check(path, b'{}\n', check=True)
            self.assertFalse(path.exists())
            write_or_check(path, b'{}\n', check=False)
            write_or_check(path, b'{}\n', check=True)
            with self.assertRaises(OracleError):
                write_or_check(path, b'{"changed":true}\n', check=True)
            self.assertEqual(path.read_bytes(), b'{}\n')

    def test_text_checkout_crlf_is_normalized_but_binary_and_content_are_not(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "output.json"
            path.write_bytes(b'{\r\n  "value": 1\r\n}\r\n')
            write_or_check(path, b'{\n  "value": 1\n}\n', check=True)
            with self.assertRaises(OracleError):
                write_or_check(path, b'{\n  "value": 2\n}\n', check=True)
            binary = Path(root) / "output.meshcoreone"
            binary.write_bytes(b"\r\n")
            with self.assertRaises(OracleError):
                write_or_check(binary, b"\n", check=True)


class FrozenInventoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reference = FrozenReference()
        cls.catalog, cls.details = generate(cls.reference)

    def test_all_original_paths_and_exact_blobs_are_accountable(self):
        self.assertEqual(self.details["counts"]["paths"], 468)
        self.assertEqual(self.details["counts"]["test_paths"], 428)
        self.assertEqual(self.details["counts"]["support_paths"], 40)
        self.assertEqual(len(self.catalog["entries"]), 468)
        for entry in self.catalog["entries"]:
            self.assertEqual(entry["blob_sha"], self.reference.blobs[entry["path"]])

    def test_every_lexical_test_annotation_is_registered(self):
        sources = self.reference.read_many(entry["path"] for entry in self.catalog["entries"])
        lexical = sum(
            token.text == "@" and tokens[index + 1].text == "Test"
            for text in sources.values() for tokens in [lex(text)]
            for index, token in enumerate(tokens[:-1])
        )
        self.assertEqual(self.details["counts"]["case_declarations"], lexical)
        self.assertGreater(lexical, 0)

    def test_original_parameter_inputs_and_pending_status_are_real(self):
        cases = [case for file in self.details["files"] for case in file["cases"]]
        self.assertTrue(all(case["port_status"] == "pending" for case in cases))
        self.assertTrue(any(case["inputs"] for case in cases))
        named = [axis for case in cases if case["inputs"] for axis in case["inputs"]["axes"] if axis["kind"] == "named-collection"]
        self.assertEqual({axis["definition"]["symbol"] for axis in named}, {"kindMatrix", "hashFlipScenarios"})
        self.assertEqual(self.details["counts"]["ported_cases"], 0)

    def test_existing_controller_accepts_structure_but_blocks_missing_parity_results(self):
        from controller.gates import validate_cases
        from controller.model import load_manifest
        manifest = load_manifest(REPO)
        with self.assertRaisesRegex(PortError, "Missing original-test results"):
            validate_cases({"source_tests": []}, manifest, manifest.wp("WP-004"), self.catalog, set())

    def test_eventual_conditions_in_other_wp_suites_are_not_dropped_or_faked(self):
        file = next(file for file in self.details["files"] if file["path"].endswith("/SettingsServiceEventStreamTests.swift"))
        self.assertEqual(file["cases"][0]["assertion_status"], "helper")
        self.assertEqual(file["cases"][0]["port_status"], "pending")
        self.assertEqual(file["cases"][0]["helper_assertions"][0]["method"], "waitUntil")
        from controller.gates import validate_cases
        from controller.model import load_manifest
        manifest = load_manifest(REPO)
        for wp in manifest.data["work_packages"]:
            originals = [entry for entry in manifest.inputs(wp["id"], cross_references=False)
                         if entry["kind"] in ("test", "support") and entry["exclusion"] is None]
            if originals:
                with self.subTest(wp=wp["id"]), self.assertRaisesRegex(PortError, "Missing original-test results"):
                    validate_cases({"source_tests": []}, manifest, wp, self.catalog, set())

    def test_source_byte_counts_measure_utf8_not_codepoints(self):
        sources = self.reference.read_many(file["path"] for file in self.details["files"])
        self.assertTrue(any(len(text.encode("utf-8")) > len(text) for text in sources.values()))
        for file in self.details["files"]:
            self.assertEqual(file["utf8_bytes"], len(sources[file["path"]].encode("utf-8")))

    def test_source_pin_cannot_be_advanced_by_cli_input(self):
        with self.assertRaisesRegex(OracleError, "advancement"):
            FrozenReference(source_sha="f" * 40)

    def test_changed_checkout_source_is_not_hidden_by_pinned_git_blobs(self):
        path = self.catalog["entries"][0]["path"]
        with patch.object(Path, "read_bytes", return_value=b"changed reference\n"):
            with self.assertRaisesRegex(OracleError, "source drift"):
                self.reference.read_many([path])

    def test_missing_blob_and_unknown_source_fail_explicitly(self):
        with self.assertRaisesRegex(OracleError, "Unknown"):
            self.reference.read_many(["MC1Tests/UnknownTests.swift"])
        entry = copy.deepcopy(self.reference.inventory[self.catalog["entries"][0]["path"]])
        self.reference.inventory[entry["path"]]["blob_sha"] = "f" * 40
        try:
            with self.assertRaisesRegex(OracleError, "stale"):
                self.reference.read_many([entry["path"]])
        finally:
            self.reference.inventory[entry["path"]] = entry

    def test_source_path_rename_is_not_a_new_support_exclusion(self):
        from oracle.reference import git
        real_git = git

        def renamed(repo, *arguments, **kwargs):
            output = real_git(repo, *arguments, **kwargs)
            if arguments == ("ls-files", "-z"):
                return output.replace(self.catalog["entries"][0]["path"].encode(), b"MC1Tests/RenamedTests.swift")
            return output

        with patch("oracle.reference.git", side_effect=renamed):
            with self.assertRaisesRegex(OracleError, "renamed"):
                self.reference.test_entries()
