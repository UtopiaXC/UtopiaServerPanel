#!/usr/bin/env python3
"""
CI/CD helper utilities for reading versions.json, validating artifacts,
and generating the GitHub Release body with compatibility matrix.
Zero third-party Python dependencies required (stdlib only).
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


def load_versions(repo_root: Path) -> dict:
    with (repo_root / "versions.json").open("r", encoding="utf-8") as fh:
        return json.load(fh)


def emit_matrices(repo_root: Path, github_output: Optional[str] = None) -> None:
    manifest = load_versions(repo_root)
    legacy = []
    modern = []

    for entry in manifest["baselines"]:
        era = entry["era"]
        mc = entry["mc"]
        for loader in entry["loaders"]:
            if era == "legacy":
                project_dir = f"legacy/{loader}-{mc}"
            else:
                project_dir = f"platforms/{loader}"
            item = {
                "mc": mc,
                "loader": loader,
                "era": era,
                "jdk": int(entry["jdk"]),
                "gradle_jdk": int(entry.get("gradleJdk", entry["jdk"])),
                "range": entry["fabricRange"] if loader == "fabric" else entry["minecraftRange"],
                "project_dir": project_dir,
            }
            if era == "legacy":
                legacy.append(item)
            else:
                modern.append(item)

    legacy_json = json.dumps(legacy, separators=(",", ":"))
    modern_json = json.dumps(modern, separators=(",", ":"))

    if github_output:
        with open(github_output, "a", encoding="utf-8") as out:
            out.write(f"legacy_matrix={legacy_json}\n")
            out.write(f"modern_matrix={modern_json}\n")
    else:
        print(f"legacy_matrix={legacy_json}")
        print(f"modern_matrix={modern_json}")


def resolve_release_note(repo_root: Path, tag: str, clean_version: str) -> Optional[Path]:
    note_dir = repo_root / "assets" / "release_note"
    candidates = [
        note_dir / f"{tag}.md",
        note_dir / f"{clean_version}.md",
    ]
    for path in candidates:
        if path.is_file():
            return path
    return None


def verify_artifacts(repo_root: Path, version: str, artifacts_dir: Path) -> Dict[Tuple[str, str], Path]:
    manifest = load_versions(repo_root)
    max_bytes = int(manifest.get("maxJarBytes", 10_000_000))
    prefix = manifest.get("artifactPrefix", "UtopiaServerPanel")

    found: Dict[Tuple[str, str], Path] = {}
    errors: List[str] = []

    for entry in manifest["baselines"]:
        mc = entry["mc"]
        for loader in entry["loaders"]:
            expected_name = f"{prefix}-{version}-mc{mc}-{loader}.jar"
            jar_path = artifacts_dir / expected_name
            if not jar_path.is_file():
                errors.append(f"Missing artifact: {expected_name}")
                continue
            size = jar_path.stat().st_size
            if size <= 0:
                errors.append(f"Empty artifact: {expected_name}")
            elif size >= max_bytes:
                errors.append(
                    f"Artifact too large: {expected_name} ({size} bytes >= limit {max_bytes} bytes)"
                )
            else:
                found[(mc, loader)] = jar_path

    if errors:
        for err in errors:
            print(f"ERROR: {err}", file=sys.stderr)
        raise SystemExit(1)

    return found


def render_release_body(
    repo_root: Path,
    tag: str,
    version: str,
    note_file: Optional[str],
    artifacts_dir: Path,
    output_file: Path,
    strict_artifacts: bool,
) -> None:
    manifest = load_versions(repo_root)
    prefix = manifest.get("artifactPrefix", "UtopiaServerPanel")

    note_text = ""
    if note_file:
        p = Path(note_file)
        if not p.is_absolute():
            p = repo_root / p
        if p.is_file():
            note_text = p.read_text(encoding="utf-8").strip()

    if not note_text:
        discovered = resolve_release_note(repo_root, tag, version)
        if discovered is not None:
            note_text = discovered.read_text(encoding="utf-8").strip()

    if strict_artifacts:
        found = verify_artifacts(repo_root, version, artifacts_dir)
    else:
        found = {}
        if artifacts_dir.is_dir():
            for entry in manifest["baselines"]:
                mc = entry["mc"]
                for loader in entry["loaders"]:
                    p = artifacts_dir / f"{prefix}-{version}-mc{mc}-{loader}.jar"
                    if p.is_file():
                        found[(mc, loader)] = p

    lines: List[str] = []
    if note_text:
        lines.append(note_text)
    else:
        lines.append(f"# {manifest.get('modName', 'Utopia Server Panel')} {tag}")
    lines.append("")
    lines.append("## Build Matrix")
    lines.append("")
    lines.append("| Baseline | Compatible Range | Loader | Java | Artifact | Size |")
    lines.append("| --- | --- | --- | --- | --- | --- |")

    for entry in manifest["baselines"]:
        mc = entry["mc"]
        jdk = entry["jdk"]
        for loader in entry["loaders"]:
            mc_range = entry["fabricRange"] if loader == "fabric" else entry["minecraftRange"]
            jar_name = f"{prefix}-{version}-mc{mc}-{loader}.jar"
            jar_path = found.get((mc, loader))
            if jar_path is not None and jar_path.is_file():
                size_mb = f"{jar_path.stat().st_size / 1_000_000:.2f} MB"
            else:
                size_mb = "-"
            lines.append(f"| {mc} | `{mc_range}` | {loader} | Java {jdk}+ | `{jar_name}` | {size_mb} |")

    lines.append("")
    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="UtopiaServerPanel CI helpers")
    sub = parser.add_subparsers(dest="command", required=True)

    p_emit = sub.add_parser("emit-matrices")
    p_emit.add_argument("--github-output", default=os.environ.get("GITHUB_OUTPUT"))

    p_render = sub.add_parser("render-release-body")
    p_render.add_argument("--tag", required=True)
    p_render.add_argument("--version", required=True)
    p_render.add_argument("--note", default="")
    p_render.add_argument("--artifacts-dir", required=True)
    p_render.add_argument("--output", required=True)
    p_render.add_argument("--no-strict", action="store_true")

    args = parser.parse_args()
    repo_root = Path(__file__).resolve().parent.parent

    if args.command == "emit-matrices":
        emit_matrices(repo_root, args.github_output)
        return 0
    if args.command == "render-release-body":
        render_release_body(
            repo_root=repo_root,
            tag=args.tag,
            version=args.version,
            note_file=args.note or None,
            artifacts_dir=Path(args.artifacts_dir),
            output_file=Path(args.output),
            strict_artifacts=not args.no_strict,
        )
        return 0
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
