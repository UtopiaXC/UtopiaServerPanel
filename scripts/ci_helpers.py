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
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple
from urllib.parse import quote

CURSEFORGE_UPLOAD_BASE_URL = "https://minecraft.curseforge.com"


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


def read_release_note_text(
    repo_root: Path, tag: str, version: str, note_file: Optional[str]
) -> str:
    """Resolve release note text from explicit --note or assets/release_note/."""
    if note_file:
        p = Path(note_file)
        if not p.is_absolute():
            p = repo_root / p
        if p.is_file():
            return p.read_text(encoding="utf-8").strip()

    discovered = resolve_release_note(repo_root, tag, version)
    if discovered is not None:
        return discovered.read_text(encoding="utf-8").strip()
    return ""


def render_release_body(
    repo_root: Path,
    tag: str,
    version: str,
    note_file: Optional[str],
    artifacts_dir: Path,
    output_file: Path,
    strict_artifacts: bool,
    repo: Optional[str] = None,
    server_url: Optional[str] = None,
) -> None:
    manifest = load_versions(repo_root)
    prefix = manifest.get("artifactPrefix", "UtopiaServerPanel")
    resolved_repo = (repo or os.environ.get("GITHUB_REPOSITORY", "")).strip().strip("/")
    resolved_server = (server_url or os.environ.get("GITHUB_SERVER_URL", "https://github.com")).strip().rstrip("/")

    note_text = read_release_note_text(repo_root, tag, version, note_file)

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
            if resolved_repo and tag:
                download_url = f"{resolved_server}/{resolved_repo}/releases/download/{quote(tag, safe='.-_~')}/{quote(jar_name, safe='.-_~')}"
                artifact_cell = f"[`{jar_name}`]({download_url})"
            else:
                artifact_cell = f"`{jar_name}`"
            lines.append(f"| {mc} | `{mc_range}` | {loader} | Java {jdk}+ | {artifact_cell} | {size_mb} |")

    lines.append("")
    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text("\n".join(lines), encoding="utf-8")


@dataclass
class CurseForgeCatalog:
    """Indexed CurseForge version IDs grouped by Minecraft, modloader, Java, and environment."""

    mc: Dict[str, int]
    loaders: Dict[str, int]
    java: Dict[str, int]
    env: Dict[str, int]


def curseforge_release_type(tag: str) -> str:
    lower = tag.lower()
    if "alpha" in lower:
        return "alpha"
    if "beta" in lower or "-rc" in lower or "-pre" in lower:
        return "beta"
    return "release"


def _cf_get_json(url: str, token: str) -> list:
    req = urllib.request.Request(
        url,
        headers={
            "X-Api-Token": token,
            "Accept": "application/json",
            "User-Agent": "UtopiaServerPanel-CI/1.0",
        },
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def index_curseforge_catalog(version_types: Sequence[dict], versions: Sequence[dict]) -> CurseForgeCatalog:
    """Build deterministic version lookup maps partitioned by gameVersionTypeID."""
    mc_type_ids = set()
    loader_type_ids = set()
    java_type_ids = set()
    env_type_ids = set()

    for vt in version_types:
        vt_id = vt.get("id")
        slug = str(vt.get("slug", "")).lower()
        name = str(vt.get("name", "")).lower()
        if slug in {"modloader", "modloaders"} or name in {"modloader", "modloaders"}:
            loader_type_ids.add(vt_id)
        elif slug == "java" or name == "java":
            java_type_ids.add(vt_id)
        elif slug in {"environment", "environments"} or "environment" in name:
            env_type_ids.add(vt_id)
        elif (slug.startswith("minecraft-") or slug == "minecraft" or name.startswith("minecraft ")) and not any(
            bad in slug for bad in ("snapshot", "bukkit", "addon", "customization")
        ):
            mc_type_ids.add(vt_id)

    mc_map: Dict[str, int] = {}
    loader_map: Dict[str, int] = {}
    java_map: Dict[str, int] = {}
    env_map: Dict[str, int] = {}

    for ver in versions:
        vt_id = ver.get("gameVersionTypeID")
        ver_id = int(ver["id"])
        name = str(ver.get("name", "")).strip()
        if not name:
            continue
        lower = name.lower()
        if vt_id in mc_type_ids:
            mc_map.setdefault(name, ver_id)
        elif vt_id in loader_type_ids:
            loader_map.setdefault(lower, ver_id)
        elif vt_id in java_type_ids:
            java_map.setdefault(lower, ver_id)
        elif vt_id in env_type_ids:
            env_map.setdefault(lower, ver_id)

    return CurseForgeCatalog(mc=mc_map, loaders=loader_map, java=java_map, env=env_map)


def resolve_curseforge_game_versions(
    entry: dict, loader: str, catalog: CurseForgeCatalog
) -> Tuple[List[int], List[str], Optional[str]]:
    """Resolve CurseForge gameVersions IDs for one baseline + loader target."""
    if loader == "forge" and "curseforgeForgeVersions" in entry:
        mc_names = list(entry["curseforgeForgeVersions"])
    else:
        mc_names = list(entry.get("curseforgeVersions", [entry["mc"]]))

    matched_mc_names = [name for name in mc_names if name in catalog.mc]
    if not matched_mc_names:
        return [], [], f"Minecraft versions {mc_names} not yet available on CurseForge"

    loader_id = catalog.loaders.get(loader.lower())
    if loader_id is None:
        raise RuntimeError(f"CurseForge catalog does not contain modloader '{loader}'")

    ids: List[int] = [catalog.mc[name] for name in matched_mc_names]
    ids.append(loader_id)

    java_id = catalog.java.get(f"java {int(entry['jdk'])}")
    if java_id is not None:
        ids.append(java_id)

    for env_name in ("server", "client"):
        env_id = catalog.env.get(env_name)
        if env_id is not None:
            ids.append(env_id)

    deduped = list(dict.fromkeys(ids))
    return deduped, matched_mc_names, None


def build_curseforge_metadata(
    entry: dict,
    loader: str,
    version: str,
    jar_name: str,
    changelog: str,
    release_type: str,
    game_version_ids: List[int],
) -> dict:
    """Build CurseForge upload metadata payload."""
    loader_title = {"forge": "Forge", "neoforge": "NeoForge", "fabric": "Fabric"}.get(loader, loader)
    payload: dict = {
        "changelog": changelog,
        "changelogType": "markdown",
        "displayName": f"UtopiaServerPanel {version} (MC {entry['mc']} {loader_title})",
        "releaseType": release_type,
        "gameVersions": game_version_ids,
    }
    if loader == "fabric":
        payload["relations"] = {
            "projects": [
                {
                    "slug": "fabric-api",
                    "type": "requiredDependency",
                }
            ]
        }
    return payload


def encode_multipart_upload(metadata: dict, jar_path: Path) -> Tuple[bytes, str]:
    """Encode CurseForge multipart/form-data request body without third-party dependencies."""
    boundary = f"----UtopiaServerPanelCF{uuid.uuid4().hex}"
    meta_bytes = json.dumps(metadata, ensure_ascii=False).encode("utf-8")
    file_bytes = jar_path.read_bytes()

    parts = [
        (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="metadata"\r\n'
            f"Content-Type: application/json; charset=utf-8\r\n\r\n"
        ).encode("utf-8"),
        meta_bytes,
        b"\r\n",
        (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; filename="{jar_path.name}"\r\n'
            f"Content-Type: application/java-archive\r\n\r\n"
        ).encode("utf-8"),
        file_bytes,
        f"\r\n--{boundary}--\r\n".encode("utf-8"),
    ]
    return b"".join(parts), boundary


def upload_curseforge_file(
    base_url: str, project_id: str, token: str, metadata: dict, jar_path: Path
) -> dict:
    """Upload one artifact to CurseForge with transient error/rate-limit retries."""
    url = f"{base_url.rstrip('/')}/api/projects/{project_id}/upload-file"
    body, boundary = encode_multipart_upload(metadata, jar_path)
    retryable = {429, 500, 502, 503, 504}

    for attempt in range(1, 5):
        req = urllib.request.Request(
            url,
            data=body,
            headers={
                "X-Api-Token": token,
                "Content-Type": f"multipart/form-data; boundary={boundary}",
                "Accept": "application/json",
                "User-Agent": "UtopiaServerPanel-CI/1.0",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                raw = resp.read().decode("utf-8").strip()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as err:
            err_body = err.read().decode("utf-8", errors="replace").strip()
            if err.code in retryable and attempt < 4:
                retry_after = err.headers.get("Retry-After") if err.headers else None
                delay = float(retry_after) if retry_after and retry_after.isdigit() else float(3 * attempt)
                print(
                    f"Retryable HTTP {err.code} uploading {jar_path.name} (attempt {attempt}/4), waiting {delay}s: {err_body}",
                    file=sys.stderr,
                )
                time.sleep(delay)
                continue
            raise RuntimeError(f"CurseForge upload failed for {jar_path.name}: HTTP {err.code} {err_body}") from err
        except urllib.error.URLError as err:
            if attempt < 4:
                delay = float(3 * attempt)
                print(
                    f"Network error uploading {jar_path.name} (attempt {attempt}/4), waiting {delay}s: {err}",
                    file=sys.stderr,
                )
                time.sleep(delay)
                continue
            raise RuntimeError(f"CurseForge upload network error for {jar_path.name}: {err}") from err

    raise RuntimeError(f"CurseForge upload exhausted retries for {jar_path.name}")


def publish_curseforge(
    repo_root: Path,
    project_id: str,
    token: str,
    tag: str,
    version: str,
    note_file: Optional[str],
    artifacts_dir: Path,
    dry_run: bool = False,
    base_url: str = CURSEFORGE_UPLOAD_BASE_URL,
    summary_file: Optional[Path] = None,
) -> None:
    manifest = load_versions(repo_root)
    prefix = manifest.get("artifactPrefix", "UtopiaServerPanel")
    found = verify_artifacts(repo_root, version, artifacts_dir)
    changelog = read_release_note_text(repo_root, tag, version, note_file) or f"Release {tag}"
    release_type = curseforge_release_type(tag)

    if dry_run and not token:
        # Synthetic catalog matching versions.json names for offline workflow validation
        catalog = CurseForgeCatalog(
            mc={
                name: idx + 1000
                for idx, name in enumerate(
                    sorted(
                        {
                            mc_name
                            for entry in manifest["baselines"]
                            for mc_name in (
                                entry.get("curseforgeVersions", []) + entry.get("curseforgeForgeVersions", [])
                            )
                        }
                    )
                )
            },
            loaders={"forge": 1, "fabric": 4, "neoforge": 6},
            java={"java 8": 2008, "java 17": 2017, "java 21": 2021, "java 25": 2025},
            env={"server": 3001, "client": 3002},
        )
    else:
        clean_base = base_url.rstrip("/")
        version_types = _cf_get_json(f"{clean_base}/api/game/version-types", token)
        versions = _cf_get_json(f"{clean_base}/api/game/versions", token)
        catalog = index_curseforge_catalog(version_types, versions)

    results: List[Tuple[str, str, str, str]] = []
    uploaded_count = 0
    total = sum(len(entry["loaders"]) for entry in manifest["baselines"])
    index = 0

    for entry in manifest["baselines"]:
        mc = entry["mc"]
        for loader in entry["loaders"]:
            index += 1
            jar_name = f"{prefix}-{version}-mc{mc}-{loader}.jar"
            jar_path = found[(mc, loader)]
            game_ids, matched_mc, skip_reason = resolve_curseforge_game_versions(entry, loader, catalog)
            if skip_reason is not None:
                print(f"[{index}/{total}] SKIP {jar_name}: {skip_reason}", file=sys.stderr)
                results.append((jar_name, loader, "-", f"Skipped ({skip_reason})"))
                continue

            metadata = build_curseforge_metadata(
                entry=entry,
                loader=loader,
                version=version,
                jar_name=jar_name,
                changelog=changelog,
                release_type=release_type,
                game_version_ids=game_ids,
            )

            if dry_run:
                print(
                    f"[{index}/{total}] DRY-RUN {jar_name}: mc={matched_mc}, gameVersions={game_ids}, "
                    f"displayName={metadata['displayName']}"
                )
                results.append((jar_name, loader, ", ".join(matched_mc), "Dry-run OK"))
                uploaded_count += 1
            else:
                resp = upload_curseforge_file(base_url, project_id, token, metadata, jar_path)
                cf_file_id = resp.get("id", "ok")
                print(f"[{index}/{total}] UPLOADED {jar_name} -> CurseForge file id {cf_file_id} (mc={matched_mc})")
                results.append((jar_name, loader, ", ".join(matched_mc), f"Uploaded (id: {cf_file_id})"))
                uploaded_count += 1
                time.sleep(0.5)

    if summary_file is not None:
        lines = [
            f"## CurseForge Distribution ({tag})",
            "",
            "| Artifact | Loader | CurseForge MC Versions | Status |",
            "| --- | --- | --- | --- |",
        ]
        for jar_name, loader, mc_text, status in results:
            lines.append(f"| `{jar_name}` | {loader} | `{mc_text}` | {status} |")
        lines.append("")
        summary_file.parent.mkdir(parents=True, exist_ok=True)
        with summary_file.open("a", encoding="utf-8") as fh:
            fh.write("\n".join(lines))

    if uploaded_count == 0:
        raise RuntimeError("No artifacts were uploaded to CurseForge.")


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
    p_render.add_argument("--repo", default="")
    p_render.add_argument("--server-url", default="")
    p_render.add_argument("--no-strict", action="store_true")

    p_cf = sub.add_parser("publish-curseforge")
    p_cf.add_argument("--project-id", required=True)
    p_cf.add_argument("--token", default="")
    p_cf.add_argument("--tag", required=True)
    p_cf.add_argument("--version", required=True)
    p_cf.add_argument("--note", default="")
    p_cf.add_argument("--artifacts-dir", required=True)
    p_cf.add_argument("--dry-run", action="store_true")
    p_cf.add_argument("--base-url", default=CURSEFORGE_UPLOAD_BASE_URL)
    p_cf.add_argument("--summary-file", default=os.environ.get("GITHUB_STEP_SUMMARY", ""))

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
            repo=args.repo or None,
            server_url=args.server_url or None,
        )
        return 0
    if args.command == "publish-curseforge":
        if not args.dry_run and not args.token:
            raise SystemExit("ERROR: CurseForge token is required unless --dry-run is set.")
        publish_curseforge(
            repo_root=repo_root,
            project_id=args.project_id,
            token=args.token,
            tag=args.tag,
            version=args.version,
            note_file=args.note or None,
            artifacts_dir=Path(args.artifacts_dir),
            dry_run=args.dry_run,
            base_url=args.base_url,
            summary_file=Path(args.summary_file) if args.summary_file else None,
        )
        return 0
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
