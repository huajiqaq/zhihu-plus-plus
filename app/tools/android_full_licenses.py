#!/usr/bin/env python3
# Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
# Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation (version 3 only).
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

"""Read locked Android JNI dependencies and their original packaged notices."""

import argparse
import hashlib
import json
import re
import subprocess
import tomllib
from pathlib import Path, PurePosixPath

NOTICE_NAME = re.compile(r"(?:licen[cs]e|copying|notice)(?:[._-].*)?", re.IGNORECASE)


def checked_original(directory, entry):
    relative = PurePosixPath(entry["path"])
    if relative.is_absolute() or "\\" in entry["path"] or any(part in ("", ".", "..") for part in entry["path"].split("/")):
        raise ValueError(f"Invalid original license path: {entry['path']}")
    path = directory
    for part in relative.parts:
        path = path / part
        if path.is_symlink():
            raise ValueError(f"Symlink in original license path: {path}")
    path.resolve().relative_to(directory.resolve())
    size = entry["bytes"]
    if not isinstance(size, int) or not 0 < size <= 2_000_000 or path.stat().st_size != size:
        raise ValueError(f"Original license size mismatch: {path}")
    with path.open("rb") as stream:
        data = stream.read(size + 1)
    if len(data) != size or hashlib.sha256(data).hexdigest() != entry["sha256"]:
        raise ValueError(f"Original license checksum mismatch: {path}")
    return data


def supplemental_notice_content(package, directory, source_manifest, locked_packages):
    sources = json.loads(source_manifest.read_text(encoding="utf-8"))
    if sources.get("version") != 1:
        raise ValueError("Unsupported Android license supplement manifest")
    source = sources["packages"].get(f"{package['name']}@{package['version']}")
    if source is None:
        raise ValueError(f"No original LICENSE/COPYING/NOTICE text in locked package {package['id']}")
    locked = [entry for entry in locked_packages if entry["name"] == package["name"]
              and entry["version"] == package["version"] and entry.get("source") == package.get("source")]
    if (len(locked) != 1 or package.get("source") != source["registry"]
            or locked[0].get("checksum") != source["crateSha256"] or package.get("license") != source["license"]):
        raise ValueError(f"License supplement does not match locked package {package['id']}")
    for evidence in source["packageEvidence"]:
        checked_original(directory, evidence)
    contents = []
    for root, entries in ((directory, source["packageNotices"]), (source_manifest.parent, source["upstreamLicenses"])):
        for entry in entries:
            data = checked_original(root, entry)
            prefix = entry.get("noticePrefixBytes", len(data))
            if not isinstance(prefix, int) or not 0 < prefix <= len(data):
                raise ValueError(f"Invalid original notice boundary: {entry['path']}")
            text = data[:prefix].decode("utf-8")
            if not text.strip():
                raise ValueError(f"Empty original notice: {entry['path']}")
            contents.append(f"--- {entry['path']} ---\n{text}")
    if not source["upstreamLicenses"] or not contents:
        raise ValueError(f"Missing original supplemental license text for {package['id']}")
    return "\n\n".join(contents)


def notice_content(package, source_manifest, locked_packages):
    directory = Path(package["manifest_path"]).resolve().parent
    candidates = directory.iterdir() if package.get("source") is None else directory.rglob("*")
    paths = {path for path in candidates if path.is_file() and NOTICE_NAME.fullmatch(path.name)}
    declared = package.get("license_file")
    if declared:
        paths.add(directory / declared)
    contents = []
    for path in sorted(paths):
        resolved = path.resolve()
        try:
            relative = resolved.relative_to(directory)
        except ValueError as error:
            raise ValueError(f"License path escapes package {package['id']}: {path}") from error
        text = resolved.read_bytes().decode("utf-8")
        if text.strip():
            contents.append(f"--- {relative.as_posix()} ---\n{text}")
    if not contents:
        return supplemental_notice_content(package, directory, source_manifest, locked_packages)
    return "\n\n".join(contents)


def android_packages(cargo, manifest, targets):
    selected = {}
    for target in targets:
        result = subprocess.run(
            [cargo, "metadata", "--format-version", "1", "--locked", "--offline",
             "--manifest-path", str(manifest), "--filter-platform", target],
            cwd=manifest.parent,
            check=True,
            stdout=subprocess.PIPE,
            encoding="utf-8",
        )
        metadata = json.loads(result.stdout)
        packages = {package["id"]: package for package in metadata["packages"]}
        resolution = metadata["resolve"]
        root = resolution["root"]
        if root is None or Path(packages[root]["manifest_path"]).resolve() != manifest:
            raise ValueError("Cargo metadata did not resolve the requested tokenizer workspace")
        nodes = {node["id"]: node for node in resolution["nodes"]}
        pending = [root]
        visited = set()
        while pending:
            package_id = pending.pop()
            if package_id in visited:
                continue
            visited.add(package_id)
            package = packages[package_id]
            selected[package_id] = package
            for dependency in nodes[package_id]["deps"]:
                # Cargo applies target predicates; only shipped dependency edges belong here.
                if any(kind["kind"] is None for kind in dependency["dep_kinds"]):
                    pending.append(dependency["pkg"])
    return sorted(selected.values(), key=lambda package: (package["name"], package["version"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cargo", default="cargo")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--sentence-license", type=Path, required=True)
    parser.add_argument("--license-supplements", type=Path, required=True)
    parser.add_argument("--target", action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    manifest = args.manifest.resolve()
    if manifest.parent.name != "rs-hf-tokenizer":
        raise ValueError("Only the Android HF tokenizer workspace is supported")
    locked_packages = tomllib.loads(manifest.with_name("Cargo.lock").read_text(encoding="utf-8"))["package"]
    libraries = []
    licenses = {}

    def add_library(unique_id, name, license_name, content, version=None, website=None):
        if not content.strip():
            raise ValueError(f"Missing original license text for {unique_id}")
        license_id = "android-full-" + hashlib.sha256(content.encode("utf-8")).hexdigest()
        licenses[license_id] = {"name": license_name, "content": content, "hash": license_id}
        library = {"uniqueId": unique_id, "name": name, "licenses": [license_id]}
        if version:
            library["artifactVersion"] = version
        if website:
            library["website"] = website
        libraries.append(library)

    add_library(
        "android-full:sentence-embeddings-android",
        "Sentence-Embeddings-Android",
        "Apache-2.0",
        args.sentence_license.read_bytes().decode("utf-8"),
        website="https://github.com/shubham0204/Sentence-Embeddings-Android",
    )
    packages = android_packages(args.cargo, manifest, args.target)
    # The previously displayed direct crates must not silently disappear during migration.
    required = {"tokenizers", "jni", "bytes", "serde", "serde_json"}
    missing = required - {package["name"] for package in packages}
    if missing:
        raise ValueError(f"Tokenizer dependency graph lost expected crates: {sorted(missing)}")
    for package in packages:
        local_wrapper = Path(package["manifest_path"]).resolve() == manifest
        add_library(
            f"android-full:rust:{package['name']}:{package['version']}",
            "huggingface/tokenizers" if package["name"] == "tokenizers" else package["name"],
            package.get("license") or ("Apache-2.0" if local_wrapper else "Packaged license"),
            notice_content(package, args.license_supplements.resolve(), locked_packages),
            version=package["version"],
            website=package.get("homepage") or package.get("repository"),
        )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps({"libraries": libraries, "licenses": licenses}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
