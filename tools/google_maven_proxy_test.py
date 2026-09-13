import sys
import threading
import time
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import google_maven_proxy as proxy


class MirrorHandlerInitializationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.handler = proxy.MirrorHandler
        with self.handler._mirror_index_condition:
            self.handler.cache_dir = Path("/tmp/androidsa-google-maven-proxy-test")
            self.handler.donors = (proxy.Donor("owner", "repo", "ref"),)
            self.handler._config_generation += 1
            self.handler._mirror_index = None
            self.handler._mirror_index_initializing_generation = None
            self.handler._mirror_index_condition.notify_all()

    def test_competing_requests_share_one_initialization(self) -> None:
        build_started = threading.Event()
        release_build = threading.Event()
        call_count = 0
        call_count_lock = threading.Lock()
        built_index = object()
        results: list[object] = []
        errors: list[BaseException] = []

        def build_index(*_args: object, **_kwargs: object) -> object:
            nonlocal call_count
            with call_count_lock:
                call_count += 1
            build_started.set()
            release_build.wait(timeout=5)
            return built_index

        def worker() -> None:
            try:
                results.append(self.handler._get_mirror_index())
            except BaseException as exc:  # pragma: no cover - assertion collects unexpected failures
                errors.append(exc)

        with mock.patch.object(proxy, "MavenMirrorIndex", side_effect=build_index):
            first = threading.Thread(target=worker)
            second = threading.Thread(target=worker)

            first.start()
            self.assertTrue(build_started.wait(timeout=5))
            second.start()
            time.sleep(0.2)
            with call_count_lock:
                self.assertEqual(call_count, 1)

            release_build.set()
            first.join(timeout=5)
            second.join(timeout=5)

        self.assertFalse(errors)
        self.assertEqual(results, [built_index, built_index])

    def test_waiting_requests_retry_after_initialization_failure(self) -> None:
        build_started = threading.Event()
        release_failure = threading.Event()
        second_build_started = threading.Event()
        call_count = 0
        call_count_lock = threading.Lock()
        fresh_index = object()
        results: list[object] = []
        errors: list[BaseException] = []

        def build_index(*_args: object, **_kwargs: object) -> object:
            nonlocal call_count
            with call_count_lock:
                call_count += 1
                current_call = call_count
            if current_call == 1:
                build_started.set()
                release_failure.wait(timeout=5)
                raise RuntimeError("boom")
            second_build_started.set()
            return fresh_index

        def worker(store_errors: bool) -> None:
            try:
                results.append(self.handler._get_mirror_index())
            except BaseException as exc:
                if store_errors:
                    errors.append(exc)
                else:  # pragma: no cover - assertion collects unexpected failures
                    raise

        with mock.patch.object(proxy, "MavenMirrorIndex", side_effect=build_index):
            first = threading.Thread(target=worker, args=(True,))
            second = threading.Thread(target=worker, args=(False,))

            first.start()
            self.assertTrue(build_started.wait(timeout=5))
            second.start()
            time.sleep(0.2)
            self.assertFalse(second_build_started.is_set())

            release_failure.set()
            self.assertTrue(second_build_started.wait(timeout=5))
            first.join(timeout=5)
            second.join(timeout=5)

        self.assertEqual(len(errors), 1)
        self.assertIsInstance(errors[0], RuntimeError)
        self.assertEqual(str(errors[0]), "boom")
        self.assertEqual(results, [fresh_index])
        with call_count_lock:
            self.assertEqual(call_count, 2)

    def test_stale_initialization_is_discarded_after_reconfiguration(self) -> None:
        first_build_started = threading.Event()
        release_first_build = threading.Event()
        call_count = 0
        call_count_lock = threading.Lock()
        stale_index = object()
        fresh_index = object()
        results: list[object] = []

        def build_index(cache_dir: Path, _donors: tuple[proxy.Donor, ...]) -> object:
            nonlocal call_count
            with call_count_lock:
                call_count += 1
                current_call = call_count
            if current_call == 1:
                first_build_started.set()
                release_first_build.wait(timeout=5)
                return stale_index
            self.assertEqual(cache_dir, Path("/tmp/androidsa-google-maven-proxy-test-fresh"))
            return fresh_index

        def worker() -> None:
            results.append(self.handler._get_mirror_index())

        with mock.patch.object(proxy, "MavenMirrorIndex", side_effect=build_index):
            first = threading.Thread(target=worker)
            second = threading.Thread(target=worker)

            first.start()
            self.assertTrue(first_build_started.wait(timeout=5))
            with self.handler._mirror_index_condition:
                self.handler.cache_dir = Path("/tmp/androidsa-google-maven-proxy-test-fresh")
                self.handler.donors = (proxy.Donor("owner", "repo", "fresh"),)
                self.handler._config_generation += 1
                self.handler._mirror_index = None
                self.handler._mirror_index_initializing_generation = None
                self.handler._mirror_index_condition.notify_all()

            second.start()
            release_first_build.set()
            first.join(timeout=5)
            second.join(timeout=5)

        self.assertEqual(results, [fresh_index, fresh_index])
        with call_count_lock:
            self.assertEqual(call_count, 2)


if __name__ == "__main__":
    unittest.main()
