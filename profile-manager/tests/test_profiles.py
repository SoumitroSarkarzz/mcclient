import json
import os
import stat
import uuid

import pytest

from mcprofiles.model import Store, ProfileError, official_launch_allowed


def test_profile_lifecycle_and_selection_survive_restart(tmp_path):
    store = Store(tmp_path)
    first = store.add_offline("Local build", "Test_Player")
    second = store.add_offline("Sandbox", "Tester2")
    store.rename(first["id"], "Renamed")
    store.set_username(first["id"], "Another_Name")
    store.select(first["id"])
    restored = Store(tmp_path)
    assert restored.data["selected"] == first["id"]
    assert restored.get(first["id"])["label"] == "Renamed"
    assert restored.get(first["id"])["username"] == "Another_Name"
    restored.delete(first["id"])
    assert restored.data["selected"] == second["id"]
    restored.delete(second["id"])
    assert Store(tmp_path).data["selected"] is None


@pytest.mark.parametrize("name", ["ab", "player with spaces", "x" * 17, "../player", "😃😃😃", "foo\nbar"])
def test_invalid_test_usernames_are_rejected(tmp_path, name):
    with pytest.raises(ProfileError):
        Store(tmp_path).add_offline("Test", name)


def test_no_saved_file_or_selected_id_can_authenticate_offline_profile(tmp_path):
    store = Store(tmp_path)
    profile = store.add_offline("Local", "DevPlayer")
    assert not official_launch_allowed(profile, {profile["id"]})
    assert not official_launch_allowed(profile, set())
    destination = tmp_path / "fixture.json"
    store.export_test_fixture(profile["id"], destination)
    fixture = json.loads(destination.read_text())
    assert fixture["authenticated"] is False
    assert fixture["mode"] == "local-test-only"
    assert set(fixture) == {"schema", "mode", "username", "local_profile_id", "authenticated"}


def test_microsoft_identity_cannot_be_edited_or_exported_as_offline(tmp_path):
    store = Store(tmp_path)
    p = store.add_microsoft("account-ref", "RealPlayer", uuid.uuid4().hex)
    store.rename(p["id"], "Friendly label")
    assert p["username"] == "RealPlayer"
    with pytest.raises(ProfileError):
        store.set_username(p["id"], "SomeoneElse")
    with pytest.raises(ProfileError):
        store.export_test_fixture(p["id"], tmp_path / "fixture.json")
    assert not official_launch_allowed(p, set())
    assert official_launch_allowed(p, {p["id"]})
    assert not official_launch_allowed(Store(tmp_path).get(p["id"]), set())


def test_settings_are_private_and_contain_no_tokens(tmp_path):
    store = Store(tmp_path / "settings")
    store.add_offline("Test", "Tester")
    assert stat.S_IMODE(store.path.stat().st_mode) == 0o600
    assert stat.S_IMODE(store.path.parent.stat().st_mode) == 0o700
    assert "token" not in store.path.read_text().lower()


def test_corrupt_file_is_not_overwritten(tmp_path):
    settings = tmp_path / "profiles.json"
    settings.write_text("broken JSON")
    with pytest.raises(ProfileError):
        Store(tmp_path)
    assert settings.read_text() == "broken JSON"


def test_symbolic_link_cannot_redirect_settings_writes(tmp_path):
    target = tmp_path / "untouched"
    target.write_text("original")
    (tmp_path / "profiles.json").symlink_to(target)
    with pytest.raises(ProfileError):
        Store(tmp_path)
    assert target.read_text() == "original"


def test_duplicate_microsoft_signin_does_not_duplicate_profile(tmp_path):
    store = Store(tmp_path)
    original = store.add_microsoft("account", "Player1", "0" * 32)
    updated = store.add_microsoft("account", "Player2", "0" * 32)
    assert original["id"] == updated["id"]
    assert len(store.profiles) == 1
    assert updated["username"] == "Player2"
