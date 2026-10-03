import fnmatch
import re

from .errors import PortError


def git_path(path: str, subtree: bool = False) -> str:
    if not isinstance(path, str) or not path:
        raise PortError("Empty path")
    if any(ord(c) < 32 or ord(c) == 127 or c in '\\:<>|"'
           for c in path) or path.startswith("/"):
        raise PortError(f"Not a repository-relative Git path: {path!r}")
    if subtree and path.endswith("/**"):
        path = path[:-2]
    if any(c in path for c in "*?[]"):
        raise PortError(f"Only literal paths or trailing /** subtrees are allowed: {path}")
    parts = path.rstrip("/").split("/")
    if any(p in ("", ".", "..") or p.endswith((" ", ".")) for p in parts):
        raise PortError(f"Unsafe path: {path!r}")
    if any(p.casefold() == ".git" for p in parts):
        raise PortError("Git-internal files cannot be leased or used as port inputs")
    if any(re.fullmatch(r"(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\..*)?", p)
           for p in parts):
        raise PortError(f"Windows-reserved path: {path}")
    if not subtree and path.endswith("/"):
        raise PortError(f"Expected a file path: {path}")
    return path


def overlaps(left: str, right: str) -> bool:
    left = git_path(left, subtree=True).rstrip("/").casefold()
    right = git_path(right, subtree=True).rstrip("/").casefold()
    return (
        left == right
        or right.startswith(left + "/")
        or left.startswith(right + "/")
    )


def conflicts(left: list[str], right: list[str]) -> list[tuple[str, str]]:
    return [(a, b) for a in left for b in right if overlaps(a, b)]


def permits(write_paths: list[str], file_path: str) -> bool:
    file_path = git_path(file_path).casefold()
    return any(
        file_path == (p := git_path(allowed, subtree=True).casefold())
        or (p.endswith("/") and file_path.startswith(p))
        for allowed in write_paths
    )


def validate_writes(write_paths: list[str], changed_paths: list[str]):
    if not write_paths or not changed_paths:
        raise PortError("Write lease and changed-path evidence must be nonempty")
    outside = [p for p in changed_paths if not permits(write_paths, p)]
    if outside:
        raise PortError(f"Changes outside the all-write-path lease: {outside}")


def expand_selectors(selectors: list[dict], source_paths: list[str], known_wps: set[str]):
    if not selectors:
        raise PortError("Empty ownership selectors")
    owners = {}
    for selector in selectors:
        pattern = selector.get("pattern")
        owner = selector.get("owner")
        if not isinstance(pattern, str) or not pattern:
            raise PortError("Empty ownership selector")
        if owner not in known_wps:
            raise PortError(f"Unknown owner: {owner}")
        matches = [p for p in source_paths if fnmatch.fnmatchcase(p, pattern)]
        if not matches:
            raise PortError(f"Unmatched ownership selector: {pattern}")
        for path in matches:
            if path in owners:
                raise PortError(f"Duplicate source owner: {path}")
            owners[path] = owner
    unowned = set(source_paths) - owners.keys()
    if unowned:
        raise PortError(f"Unowned source paths: {sorted(unowned)}")
    return owners
