import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

work = Path("/home/cbattagler/meshcoreone-work")
repo = work / "local-checks/repos/ef5b4cfe42514537"
expected_head = "75193694057a91618185dacb2a03506d49054569"
outputs = {
    "android/gradle/dependency-locks/app.lockfile",
    "android/gradle/verification-metadata.xml",
}

def sha(data):
    return hashlib.sha256(data).hexdigest()

def tuples(data):
    root = ET.fromstring(data)
    result = set()
    for component in root.findall(".//{*}component"):
        for artifact in component.findall("{*}artifact"):
            for checksum in artifact.findall("{*}sha256"):
                result.add((component.get("group"), component.get("name"),
                            component.get("version"), artifact.get("name"), checksum.get("value")))
    if not result:
        raise RuntimeError("Zero checksum tuples")
    return result

with (work / "local-checks/gradle.lock").open("w") as lane:
    fcntl.flock(lane, fcntl.LOCK_EX)
    if not (repo / ".git/meshcore-local-snapshot").is_dir():
        raise RuntimeError("Not an existing official managed snapshot")
    os.chdir(repo)
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if head != expected_head:
        raise RuntimeError("Stale receiver snapshot head")
    directory = Path(tempfile.mkdtemp(prefix="wp302-espresso-", dir=work / "local-checks"))
    print("Actual generation artifacts:", directory, flush=True)
    sys.path.insert(0, str(repo / "tools/android-port"))
    from controller.ci_environment import candidate_environment, verify_wrapper
    state = json.loads((work / "toolchain/environment.json").read_text())
    state["private_root"] = str(work / "local-checks/private")
    environment = candidate_environment(state)
    environment["PATH"] = os.pathsep.join([
        str(Path(state["java_home"]) / "bin"), str(Path(sys.executable).parent),
        environment.get("PATH", ""),
    ])
    environment["GRADLE_USER_HOME"] = str(work / ".gradle-fast")
    environment["GRADLE_OPTS"] = '-Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"'
    verify_wrapper()
    inputs = {}
    for row in subprocess.check_output(["git", "ls-tree", "-rz", "--full-tree", "HEAD"]).split(b"\0"):
        if not row:
            continue
        metadata, name = row.split(b"\t", 1)
        mode, kind, blob = metadata.split()
        relative = name.decode()
        data = (repo / relative).read_bytes()
        if kind != b"blob" or mode not in (b"100644", b"100755"):
            raise RuntimeError("Unsupported literal generation input")
        if hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest().encode() != blob:
            raise RuntimeError("Snapshot input differs from committed blob: " + relative)
        inputs[relative] = {"sha256": sha(data), "size": len(data), "blob": blob.decode()}
    before_xml = (repo / "android/gradle/verification-metadata.xml").read_bytes()
    for relative in outputs:
        target = directory / "before" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(repo / relative, target)
    command = [
        str(repo / "android/gradlew"), "-p", str(repo / "android"),
        ":app:resolveWp302NavigationDependencies",
        "--write-locks", "--write-verification-metadata", "sha256",
        "--dependency-verification", "strict", "--console=plain",
        "--no-parallel", "--max-workers=4",
    ]
    (directory / "command.json").write_text(json.dumps(command, indent=2) + "\n")
    print("Executing:", " ".join(command), flush=True)
    with (directory / "generation.log").open("w") as log:
        process = subprocess.Popen(command, env=environment, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True)
        for line in process.stdout:
            print(line, end="", flush=True)
            log.write(line)
        if process.wait():
            raise RuntimeError("Actual sixteen-graph generation failed")
    changed = {path for path, binding in inputs.items() if sha((repo / path).read_bytes()) != binding["sha256"]}
    if changed != outputs:
        raise RuntimeError("Unexpected generator write scope: " + repr(changed))
    graphs = repo / "android/app/build/reports/wp302/navigation-dependencies"
    files = list(graphs.glob("*.tsv"))
    if len(files) != 16 or any(len(path.read_text().splitlines()) < 2 for path in files):
        raise RuntimeError("Missing/zero actual graph")
    for path in files:
        shutil.copyfile(path, directory / path.name)
    old = tuples(before_xml)
    current = tuples((repo / "android/gradle/verification-metadata.xml").read_bytes())
    if not old.issubset(current):
        raise RuntimeError("Inherited verification tuples removed")
    published = []
    for group, name, version, artifact, checksum in sorted(current - old):
        relative = group.replace(".", "/") + "/" + name + "/" + version + "/" + artifact
        for root in ("https://dl.google.com/dl/android/maven2/", "https://repo.maven.apache.org/maven2/"):
            try:
                data = urllib.request.urlopen(root + relative).read()
                break
            except urllib.error.HTTPError as error:
                if error.code != 404:
                    raise
        else:
            raise RuntimeError("Missing independent publication: " + relative)
        if sha(data) != checksum:
            raise RuntimeError("Independent checksum mismatch: " + relative)
        target = directory / "publications" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        published.append({"url": root + relative, "sha256": checksum, "size": len(data)})
    for relative in outputs:
        target = directory / "outputs" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(repo / relative, target)
    receipt = {
        "head": head, "tree": subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], text=True).strip(),
        "inputs": inputs, "input_count": len(inputs), "command": command,
        "graphs": {path.name: sha(path.read_bytes()) for path in files},
        "changed_paths": sorted(changed), "inherited_checksum_tuples": len(old),
        "checksum_tuples": len(current), "independently_rehashed_new_artifacts": published,
        "outputs": {path: {"sha256": sha((repo / path).read_bytes()), "size": (repo / path).stat().st_size}
                    for path in outputs},
        "scope": "Real scoped test-support generation, not native parity/publication/hardware/license evidence",
    }
    (directory / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print("GENERATION PASSED:", directory, "inputs", len(inputs), "graphs", len(files),
          "new independent checksums", len(published), flush=True)
