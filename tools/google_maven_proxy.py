#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import tarfile
import tempfile
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, urlparse
from urllib.error import HTTPError, URLError
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
)

TEXT_EXTENSIONS = {".module", ".pom", ".xml"}
BINARY_EXTENSIONS = {".aar", ".jar", ".zip"}
GOOGLE_MAVEN_BASE_URLS = (
    "https://dl.google.com/dl/android/maven2",
    "https://maven.google.com",
)


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
        request = Request(url, headers={"User-Agent": "AndroidSA Google Maven Proxy"})
        with urlopen(request, timeout=60) as response, destination.open("wb") as handle:
            shutil.copyfileobj(response, handle)

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
        if metadata is not None:
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
                self._download_to_file(donor.file_url(remote_path, binary=binary), temp_path)
                os.replace(temp_path, artifact_path)
                return artifact_path
            except HTTPError as exc:
                temp_path.unlink(missing_ok=True)
                if exc.code != HTTPStatus.NOT_FOUND:
                    raise
            except Exception:
                temp_path.unlink(missing_ok=True)
                raise

        if self._download_from_google_maven(request_path, artifact_path):
            return artifact_path

        return None

    def _download_from_google_maven(self, request_path: str, artifact_path: Path) -> bool:
        artifact_path.parent.mkdir(parents=True, exist_ok=True)
        saw_not_found = False
        last_retryable_error: HTTPError | URLError | None = None

        for base_url in GOOGLE_MAVEN_BASE_URLS:
            with tempfile.NamedTemporaryFile(delete=False, dir=str(artifact_path.parent)) as temp_file:
                temp_path = Path(temp_file.name)
            try:
                self._download_to_file(f"{base_url}/{request_path}", temp_path)
                os.replace(temp_path, artifact_path)
                return True
            except HTTPError as exc:
                temp_path.unlink(missing_ok=True)
                if exc.code == HTTPStatus.NOT_FOUND:
                    saw_not_found = True
                    continue
                last_retryable_error = exc
            except URLError as exc:
                temp_path.unlink(missing_ok=True)
                last_retryable_error = exc
            except Exception:
                temp_path.unlink(missing_ok=True)
                raise

        if saw_not_found:
            return False
        if last_retryable_error is not None:
            raise last_retryable_error
        return False


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
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Length", str(artifact_path.stat().st_size))
        self.send_header("Cache-Control", "public, max-age=3600")
        self.end_headers()
        if send_body:
            self.wfile.write(data)

    def _send_text(self, status: HTTPStatus, message: str, send_body: bool) -> None:
        encoded = message.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        if send_body:
            self.wfile.write(encoded)


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
