"""Deterministic capability admission and advisory reservation reconciliation."""

from dataclasses import dataclass
from fnmatch import fnmatchcase

from .errors import PortError
from .paths import git_path
from .schema import digest, nonempty


@dataclass(frozen=True)
class Admission:
    capability_id: str
    path: str
    operation: str
    invariants: tuple[str, ...]
    validation: tuple[str, ...]


class CapabilityEngine:
    """Matches trusted policy rules; free-form authorization never participates."""

    def __init__(self, policy: dict):
        reservation = policy.get("reservation_policy")
        if not isinstance(reservation, dict):
            raise PortError("Missing reservation policy")
        capabilities = reservation.get("capabilities")
        if not isinstance(capabilities, list) or not capabilities:
            raise PortError("Missing trusted capability definitions")
        self._rules = {}
        for rule in capabilities:
            if not isinstance(rule, dict) or not isinstance(rule.get("id"), str):
                raise PortError("Malformed capability definition")
            if rule["id"] in self._rules:
                raise PortError(f"Duplicate capability definition: {rule['id']}")
            for key in ("path_rules", "operations", "invariants", "validation"):
                if not isinstance(rule.get(key), list) or not all(
                    isinstance(value, str) and value for value in rule[key]
                ):
                    raise PortError(f"Malformed {rule['id']} capability {key}")
            self._rules[rule["id"]] = rule

    @property
    def revision(self):
        return digest(self._rules)

    @staticmethod
    def _matches(pattern: str, path: str) -> bool:
        pattern = pattern.replace("\\", "/")
        path = path.replace("\\", "/")
        if pattern.endswith("/**"):
            root = pattern[:-3].rstrip("/")
            return path == root or path.startswith(root + "/")
        return fnmatchcase(path, pattern)

    def admit(self, wp_id: str, assigned_paths: list[str], path: str, operation: str) -> Admission:
        nonempty(wp_id, "Capability WP")
        path = git_path(path, subtree=True)
        nonempty(operation, "Capability operation")
        candidates = []
        if any(self._matches(allowed, path) or self._matches(path, allowed)
               for allowed in assigned_paths):
            candidates.append(self._rules.get("wp-owned"))
        candidates.extend(
            rule for rule in self._rules.values()
            if rule["id"] != "wp-owned"
            and operation in rule["operations"]
            and any(self._matches(pattern, path) for pattern in rule["path_rules"])
        )
        candidates = [
            rule for rule in candidates
            if rule is not None and operation in rule["operations"]
        ]
        if not candidates:
            raise PortError(
                f"Capability admission blocked: WP {wp_id}, path {path}, operation {operation}; "
                "no trusted path+operation rule"
            )
        rule = sorted(candidates, key=lambda value: value["id"])[0]
        return Admission(rule["id"], path, operation, tuple(rule["invariants"]), tuple(rule["validation"]))

    def admit_scope(self, wp_id: str, assigned_paths: list[str],
                    paths: list[str], operations: list[str]) -> list[Admission]:
        if not paths or not operations:
            raise PortError("Capability scope must contain paths and operations")
        result = [
            self.admit(wp_id, assigned_paths, path, operation)
            for path in sorted(set(paths))
            for operation in sorted(set(operations))
        ]
        return result

    def overlap_report(self, records: list[dict]) -> list[dict]:
        report = []
        for index, left in enumerate(records):
            for right in records[index + 1:]:
                shared = sorted({
                    (a, b) for a in left.get("paths", []) for b in right.get("paths", [])
                    if self._matches(a, b) or self._matches(b, a)
                })
                capabilities = sorted(
                    set(left.get("capabilities", {}).keys())
                    & set(right.get("capabilities", {}).keys())
                )
                if shared or capabilities:
                    report.append({
                        "left": left["wp"], "right": right["wp"],
                        "paths": [{"left": a, "right": b} for a, b in shared],
                        "capabilities": capabilities,
                        "advisory": True,
                    })
        return report

    @staticmethod
    def validate_merge(expected_base: str, current_base: str, semantic_conflicts=()):
        if expected_base != current_base:
            raise PortError("Current base changed; reconcile advisory claims against the current base")
        conflicts = sorted(set(semantic_conflicts))
        if conflicts:
            raise PortError(f"Unresolved semantic ownership collision: {conflicts}")
        return {"current_base": current_base, "semantic_conflicts": [], "merge_allowed": True}
