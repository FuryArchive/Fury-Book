"""Compile real non-Compose Shared code once; preserve internal APIs through friends.

This is test infrastructure, not a product substitute. Every compiler/runtime
failure is surfaced. A fingerprint never reuses classes after source/tool changes.
"""
from pathlib import Path
import hashlib
import os
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def _run(command, timeout=120):
    result = subprocess.run(list(map(str, command)), cwd=ROOT, capture_output=True,
                            text=True, timeout=timeout)
    assert result.returncode == 0, result.stdout + result.stderr
    return result


def runtime():
    compiler = shutil.which('kotlinc')
    assert compiler, 'kotlinc is required for the actual Shared regression harnesses'
    compiler = Path(compiler).resolve()
    coroutine = compiler.parents[1] / 'lib/kotlinx-coroutines-core-jvm.jar'
    assert coroutine.is_file(), 'Kotlin coroutine runtime is required'
    return compiler, coroutine


def shared_sources():
    folder = ROOT / 'shared/src/commonMain/kotlin'
    return sorted(path for path in folder.rglob('*.kt') if '/ui/' not in path.as_posix())


def shared_classes():
    compiler, coroutine = runtime()
    sources = shared_sources()
    version = _run([compiler, '-version'], 20)
    identity = hashlib.sha256((str(compiler)+version.stdout+version.stderr).encode())
    identity.update(coroutine.read_bytes())
    for path in sources:
        identity.update(path.relative_to(ROOT).as_posix().encode())
        identity.update(path.read_bytes())
    directory = ROOT / 'build/python-harness-cache' / identity.hexdigest()
    jar = directory / 'shared.jar'
    if not jar.is_file():
        directory.mkdir(parents=True, exist_ok=True)
        staged = directory / 'shared-building.jar'
        _run([compiler, '-language-version', '2.0', '-cp', coroutine,
              *sources, '-d', staged], 180)
        staged.replace(jar)
    return compiler, coroutine, jar


def run_harness(harness, *, extra_sources=(), args=(), main=None, timeout=40):
    compiler, coroutine, shared = shared_classes()
    harness = Path(harness)
    extra = sorted(set(Path(p) for p in extra_sources) - set(shared_sources()))
    package = re.search(r'^package\s+([\w.]+)', harness.read_text(), re.MULTILINE)
    entrypoint = main or ((package[1]+'.' if package else '') + harness.stem+'Kt')
    with tempfile.TemporaryDirectory(prefix='furybook-harness-') as temp:
        jar = Path(temp) / 'harness.jar'
        classpath = os.pathsep.join(map(str, (shared, coroutine)))
        _run([compiler, '-language-version', '2.0', '-Xfriend-paths='+str(shared),
              '-cp', classpath, *extra, harness, '-include-runtime', '-d', jar], 90)
        return _run(['java', '-cp', os.pathsep.join((str(jar), classpath)),
                     entrypoint, *args], timeout)


def assert_application_route(source, method, call):
    """Bind each adapter method to its real Shared receiver and review boundary."""
    body = source.split('fun '+method+'(', 1)[1].split('\n    fun ', 1)[0]
    if 'descriptive {' in body:
        assert call.removeprefix('application.')+'(' in body, method
        review = source.split('descriptive(edit: DublApplication.()', 1)[1].split('\n    }', 1)[0]
        assert 'onCanonicalNonFinancialReview' in review
        assert 'if (isCanonicalProtected)' in review or 'if (canonicalMode)' in review
        assert 'if (review == null)' in review and 'storageError' in review
        assert 'review(edit)' in review or 'review { edit(); Unit }' in review
        assert 'sync { application.edit() }' in review
    else:
        assert call+'(' in body, method
