import hashlib

import pytest

from mapper.perception.model_store import ModelUnavailable, ensure_model


def _published(tmp_path, content: bytes = b"onnx-model-bytes"):
    source = tmp_path / "release" / "cubicasa.onnx"
    source.parent.mkdir()
    source.write_bytes(content)
    return source.as_uri(), hashlib.sha256(content).hexdigest()


def test_downloads_and_verifies_the_model_once(tmp_path):
    url, digest = _published(tmp_path)
    target = tmp_path / "cache" / "cubicasa.onnx"

    assert ensure_model(target, url, digest) == target
    assert target.read_bytes() == b"onnx-model-bytes"

    # A verified cached copy is reused even if the release is no longer reachable.
    assert ensure_model(target, (tmp_path / "missing.onnx").as_uri(), digest) == target


def test_a_corrupt_cached_copy_is_replaced(tmp_path):
    url, digest = _published(tmp_path)
    target = tmp_path / "cubicasa.onnx"
    target.write_bytes(b"truncated")

    ensure_model(target, url, digest)

    assert target.read_bytes() == b"onnx-model-bytes"


def test_a_checksum_mismatch_never_leaves_a_model_behind(tmp_path):
    url, _ = _published(tmp_path)
    target = tmp_path / "cache" / "cubicasa.onnx"

    with pytest.raises(ModelUnavailable):
        ensure_model(target, url, "0" * 64)

    assert list(target.parent.iterdir()) == []


def test_an_unreachable_release_is_reported(tmp_path):
    with pytest.raises(ModelUnavailable):
        ensure_model(tmp_path / "cubicasa.onnx", (tmp_path / "nowhere.onnx").as_uri(), "0" * 64)
