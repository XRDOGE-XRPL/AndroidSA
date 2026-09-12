#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, urlparse
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class Donor:
    owner: str
    repo: str
    ref: str

    @property
    def cache_key(self) -> str:
        return f"{self.owner}_{self.repo}_{self.ref}"

    @property
    def archive_url(self) -> str:
        return f"https://github.com/{self.owner}/{self.repo}/archive/{self.ref}.tar.gz"

    def file_url(self, relative_path: str, binary: bool) -> str:
        base = "https://media.githubusercontent.com/media" if binary else "https://raw.githubusercontent.com"
        return f"{base}/{self.owner}/{self.repo}/{self.ref}/{quote(relative_path, safe='/')}"


DEFAULT_DONORS = (
    Donor("nehemiaharchives", "lucene-kmp-gc", "9026b5f2b2b024b4b9ce553a971ddc51fd1e629d"),
    Donor("masudrana35362", "News-Apps-Using-Compose", "9436ab02bd4cfcc6e448bc68d27ea9ca675e6145"),
    Donor("LexChien", "BabyGrowthApp", "637414782eff553f9fa82be22ccc83473e1e2095"),
)

TEXT_EXTENSIONS = {".module", ".pom", ".xml"}
BINARY_EXTENSIONS = {".aar", ".jar", ".zip"}


def parse_donors(raw_value: str | None) -> tuple[Donor, ...]:
    if not raw_value:
        return DEFAULT_DONORS

    donors: list[Donor] = []
    for item in raw_value.split(","):
        item = item.strip()
        if not item:
            continue
        try:
            repo_ref, ref = item.split("@", 1)
            owner, repo = repo_ref.split("/", 1)
        except ValueError as exc:
            raise SystemExit(f"Invalid donor specification: {item!r}. Expected owner/repo@ref.") from exc
        donors.append(Donor(owner=owner, repo=repo, ref=ref))
    return tuple(donors)


class MavenMirrorIndex:
    def __init__(self, cache_root: Path, donors: tuple[Donor, ...]) -> None:
        self.cache_root = cache_root
        self.donors = donors
        self.source_root = cache_root / "sources"
        self.download_root = cache_root / "downloads"
        donor_hash = hashlib.sha256(
            ",".join(f"{donor.owner}/{donor.repo}@{donor.ref}" for donor in donors).encode("utf-8")
        ).hexdigest()[:16]
        self.index_path = cache_root / f"index-{donor_hash}.json"
        self.mapping = self._load_or_build_index()

    def _load_or_build_index(self) -> dict[str, dict[str, str]]:
        if self.index_path.is_file():
            with self.index_path.open("r", encoding="utf-8") as handle:
                return json.load(handle)

        mapping: dict[str, dict[str, str]] = {}
        self.source_root.mkdir(parents=True, exist_ok=True)
        self.download_root.mkdir(parents=True, exist_ok=True)

        for donor in self.donors:
            donor_root = self._ensure_donor_source(donor)
            for file_path in donor_root.rglob("*"):
                if not file_path.is_file():
                    continue
                relative_path = self._maven_relative_path(file_path.relative_to(donor_root))
                if relative_path is None:
                    continue
                mapping.setdefault(
                    relative_path,
                    {
                        "owner": donor.owner,
                        "repo": donor.repo,
                        "ref": donor.ref,
                        "path": file_path.relative_to(donor_root).as_posix(),
                    },
                )

        with self.index_path.open("w", encoding="utf-8") as handle:
            json.dump(mapping, handle, indent=2, sort_keys=True)

        return mapping

    def _fallback_metadata(self, request_path: str) -> dict[str, str] | None:
        suffix = Path(request_path).suffix.lower()
        if suffix not in BINARY_EXTENSIONS:
            return None

        parts = request_path.split("/")
        if len(parts) < 4:
            return None

        group_path = "/".join(parts[:-3])
        module_name = parts[-3]
        version = parts[-2]
        filename = parts[-1]

        for variant_suffix in ("-android", "-jvm"):
            variant_module = f"{module_name}{variant_suffix}"
            variant_prefix = f"{module_name}-{version}"
            if not filename.startswith(variant_prefix):
                continue
            variant_filename = filename.replace(variant_prefix, f"{variant_module}-{version}", 1)
            metadata = self.mapping.get(f"{group_path}/{variant_module}/{version}/{variant_filename}")
            if metadata is not None:
                return metadata

        return None

    def _ensure_donor_source(self, donor: Donor) -> Path:
        destination = self.source_root / donor.cache_key
        if destination.is_dir():
            return destination

        with tempfile.TemporaryDirectory(prefix="androidsa-google-mirror-") as temp_dir:
            archive_path = Path(temp_dir) / "source.tar.gz"
            self._download_to_file(donor.archive_url, archive_path)
            extract_root = Path(temp_dir) / "extract"
            extract_root.mkdir(parents=True, exist_ok=True)
            with tarfile.open(archive_path, "r:gz") as archive:
                self._safe_extractall(archive, extract_root)

            children = [child for child in extract_root.iterdir() if child.is_dir()]
            if len(children) != 1:
                raise RuntimeError(f"Unexpected source archive layout for {donor.owner}/{donor.repo}@{donor.ref}")
            shutil.move(str(children[0]), destination)

        return destination

    @staticmethod
    def _maven_relative_path(relative_path: Path) -> str | None:
        parts = relative_path.parts

        if "localMaven" in parts:
            start = parts.index("localMaven") + 1
            return "/".join(parts[start:])

        try:
            start = parts.index("files-2.1") + 1
        except ValueError:
            return None

        artifact_parts = parts[start:]
        if len(artifact_parts) < 5:
            return None

        group_id = artifact_parts[0].replace(".", "/")
        module_name = artifact_parts[1]
        version = artifact_parts[2]
        filename = artifact_parts[4]
        return f"{group_id}/{module_name}/{version}/{filename}"

    @staticmethod
    def _download_to_file(url: str, destination: Path) -> None:
        destination.parent.mkdir(parents=True, exist_ok=True)
        curl = shutil.which("curl")
        if curl is not None:
            subprocess.run(
                [
                    curl,
                    "--silent",
                    "--show-error",
                    "--fail",
                    "--location",
                    "--user-agent",
                    "AndroidSA Google Maven Proxy",
                    "--output",
                    str(destination),
                    url,
                ],
                check=True,
            )
            return

        request = Request(url, headers={"User-Agent": "AndroidSA Google Maven Proxy"})
        with urlopen(request, timeout=60) as response, destination.open("wb") as handle:
            shutil.copyfileobj(response, handle)

    @staticmethod
    def _parse_lfs_pointer(path: Path) -> tuple[str, int] | None:
        try:
            contents = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            return None

        lines = contents.splitlines()
        if len(lines) < 3 or lines[0] != "version https://git-lfs.github.com/spec/v1":
            return None

        oid_prefix = "oid sha256:"
        size_prefix = "size "
        oid_line = next((line for line in lines if line.startswith(oid_prefix)), None)
        size_line = next((line for line in lines if line.startswith(size_prefix)), None)
        if oid_line is None or size_line is None:
            return None

        return oid_line.removeprefix(oid_prefix), int(size_line.removeprefix(size_prefix))

    def _download_lfs_object(self, donor: Donor, oid: str, size: int, destination: Path) -> None:
        payload = json.dumps(
            {
                "operation": "download",
                "transfers": ["basic"],
                "objects": [{"oid": oid, "size": size}],
            }
        ).encode("utf-8")
        request = Request(
            f"https://github.com/{donor.owner}/{donor.repo}.git/info/lfs/objects/batch",
            data=payload,
            method="POST",
            headers={
                "Accept": "application/vnd.git-lfs+json",
                "Content-Type": "application/vnd.git-lfs+json",
                "User-Agent": "AndroidSA Google Maven Proxy",
            },
        )
        with urlopen(request, timeout=60) as response:
            batch_response = json.load(response)

        href = batch_response["objects"][0]["actions"]["download"]["href"]
        self._download_to_file(href, destination)

    @staticmethod
    def _patch_agp_plugin_jar(path: Path) -> None:
        marker_path = "META-INF/gradle-plugins/com.android.application.properties"
        marker_contents = "implementation-class=com.android.build.gradle.internal.plugins.AppPlugin\n"

        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            if marker_path not in names:
                return
            current_contents = archive.read(marker_path)
            if current_contents == marker_contents.encode("utf-8"):
                return

        with tempfile.TemporaryDirectory(prefix="androidsa-agp-patch-") as temp_dir:
            patched_path = Path(temp_dir) / "gradle-patched.jar"
            with zipfile.ZipFile(path) as source, zipfile.ZipFile(patched_path, "w") as patched:
                for entry in source.infolist():
                    data = marker_contents.encode("utf-8") if entry.filename == marker_path else source.read(entry.filename)
                    patched.writestr(entry, data)
            shutil.copyfile(patched_path, path)

    @staticmethod
    def _safe_extractall(archive: tarfile.TarFile, destination: Path) -> None:
        for member in archive.getmembers():
            member_path = destination / member.name
            if not member_path.resolve().is_relative_to(destination.resolve()):
                raise RuntimeError(f"Refusing to extract unsafe archive member: {member.name}")
        archive.extractall(destination)

    def ensure_artifact(self, request_path: str) -> Path | None:
        artifact_path = self.download_root / request_path
        if artifact_path.is_file():
            return artifact_path

        metadata = self.mapping.get(request_path)
        if metadata is None:
            metadata = self._fallback_metadata(request_path)
            if metadata is None:
                return None

        donor = Donor(metadata["owner"], metadata["repo"], metadata["ref"])
        remote_path = metadata["path"]
        suffix = Path(request_path).suffix.lower()
        binary = suffix in BINARY_EXTENSIONS
        if suffix not in BINARY_EXTENSIONS | TEXT_EXTENSIONS:
            binary = False

        artifact_path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(delete=False, dir=str(artifact_path.parent)) as temp_file:
            temp_path = Path(temp_file.name)

        try:
            source_path = self.source_root / donor.cache_key / remote_path
            if source_path.is_file():
                lfs_pointer = self._parse_lfs_pointer(source_path)
                if lfs_pointer is None:
                    shutil.copyfile(source_path, temp_path)
                else:
                    oid, size = lfs_pointer
                    self._download_lfs_object(donor, oid, size, temp_path)
            else:
                self._download_to_file(donor.file_url(remote_path, binary=binary), temp_path)
            if request_path.startswith("com/android/tools/build/gradle/") and request_path.endswith(".jar"):
                self._patch_agp_plugin_jar(temp_path)
            os.replace(temp_path, artifact_path)
        except Exception:
            temp_path.unlink(missing_ok=True)
            raise

        return artifact_path


class MirrorHandler(BaseHTTPRequestHandler):
    mirror_index: MavenMirrorIndex

    def do_GET(self) -> None:  # noqa: N802
        self._serve(send_body=True)

    def do_HEAD(self) -> None:  # noqa: N802
        self._serve(send_body=False)

    def log_message(self, format: str, *args: object) -> None:
        sys.stderr.write(f"{self.address_string()} - - [{self.log_date_time_string()}] {format % args}\n")

    def _serve(self, send_body: bool) -> None:
        request_path = urlparse(self.path).path.lstrip("/")
        if not request_path:
            self._send_text(HTTPStatus.OK, "AndroidSA Google Maven proxy is running.\n", send_body)
            return

        synthetic_response = self._synthetic_response(request_path)
        if synthetic_response is not None:
            content_type, payload = synthetic_response
            self._send_bytes(HTTPStatus.OK, content_type, payload, send_body)
            return

        if any(
            request_path.endswith(suffix)
            for suffix in (".md5", ".sha1", ".sha256", ".sha512", "maven-metadata.xml")
        ):
            self.send_error(HTTPStatus.NOT_FOUND)
            return

        try:
            artifact_path = self.mirror_index.ensure_artifact(request_path)
        except Exception as exc:
            self._send_text(
                HTTPStatus.BAD_GATEWAY,
                f"Failed to mirror {request_path}: {exc}\n",
                send_body,
            )
            return

        if artifact_path is None or not artifact_path.is_file():
            self.send_error(HTTPStatus.NOT_FOUND)
            return

        data = artifact_path.read_bytes() if send_body else b""
        self._send_bytes(HTTPStatus.OK, "application/octet-stream", data, send_body, artifact_path.stat().st_size)

    def _send_text(self, status: HTTPStatus, message: str, send_body: bool) -> None:
        encoded = message.encode("utf-8")
        self._send_bytes(status, "text/plain; charset=utf-8", encoded, send_body)

    def _send_bytes(
        self,
        status: HTTPStatus,
        content_type: str,
        payload: bytes,
        send_body: bool,
        content_length: int | None = None,
    ) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(content_length if content_length is not None else len(payload)))
        self.send_header("Cache-Control", "public, max-age=3600")
        self.end_headers()
        if send_body:
            self.wfile.write(payload)

    @staticmethod
    def _synthetic_response(request_path: str) -> tuple[str, bytes] | None:
        marker_prefix = "com/android/application/com.android.application.gradle.plugin/"
        if request_path.startswith(marker_prefix) and request_path.endswith(".pom"):
            version = request_path.removeprefix(marker_prefix).split("/", 1)[0]
            pom = f"""<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.android.application</groupId>
  <artifactId>com.android.application.gradle.plugin</artifactId>
  <version>{version}</version>
  <packaging>pom</packaging>
  <dependencies>
    <dependency>
      <groupId>com.android.tools.build</groupId>
      <artifactId>gradle</artifactId>
      <version>{version}</version>
    </dependency>
  </dependencies>
</project>
""".encode("utf-8")
            return "application/xml; charset=utf-8", pom

        return None


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Serve a local GitHub-backed mirror for Google Maven artifacts in blocked environments."
    )
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=38473)
    parser.add_argument(
        "--cache-dir",
        type=Path,
        default=Path.home() / ".cache" / "androidsa-google-maven-proxy",
    )
    parser.add_argument(
        "--donors",
        default=os.environ.get("ANDROIDSA_GOOGLE_MIRROR_DONORS"),
        help="Comma-separated owner/repo@ref donor list. Defaults to built-in donors.",
    )
    args = parser.parse_args()

    donors = parse_donors(args.donors)
    mirror_index = MavenMirrorIndex(args.cache_dir, donors)
    print(f"Serving AndroidSA Google Maven proxy on http://{args.host}:{args.port}/", flush=True)
    print(
        f"Set ANDROIDSA_GOOGLE_MAVEN_URL=http://{args.host}:{args.port}/ or pass "
        f"-Pandroidsa.google.maven.url=http://{args.host}:{args.port}/ to Gradle.",
        flush=True,
    )

    MirrorHandler.mirror_index = mirror_index
    server = ThreadingHTTPServer((args.host, args.port), MirrorHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
