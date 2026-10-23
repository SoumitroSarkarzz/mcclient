import os
import time
import tkinter as tk
from unittest.mock import Mock
import uuid

import pytest

from mcprofiles.auth import AuthError
from mcprofiles.model import Store
from mcprofiles.ui import Window


pytestmark = pytest.mark.skipif(not os.environ.get("DISPLAY") and os.name != "nt", reason="Requires a desktop display")


@pytest.fixture
def window(tmp_path):
    root = tk.Tk()
    app = Window(root, Store(tmp_path))
    root.update()
    yield app
    app.close()


def finish_work(window):
    deadline = time.monotonic() + 5
    while window.busy and time.monotonic() < deadline:
        window.root.update()
        time.sleep(0.01)
    assert not window.busy


def test_offline_add_rename_edit_switch_delete_and_disabled_launch(window, monkeypatch):
    assert window.cancel_button.winfo_viewable()
    answers = iter(["TestUser", "Dev setup", "ChangedPlayer", "SecondTest"])
    monkeypatch.setattr("mcprofiles.ui.simpledialog.askstring", lambda *a, **k: next(answers))
    window.add_offline()
    first = window.selected()["id"]
    assert "OFFLINE" in window.badge.cget("text")
    assert str(window.launch.cget("state")) == "disabled"
    window.rename()
    window.edit_username()
    assert window.selected()["label"] == "Dev setup"
    assert window.selected()["username"] == "ChangedPlayer"
    window.add_offline()
    window.listbox.selection_clear(0, tk.END)
    window.listbox.selection_set(0)
    window.selection(None)
    assert window.selected()["id"] == first
    monkeypatch.setattr("mcprofiles.ui.messagebox.askyesno", lambda *a, **k: True)
    window.delete()
    assert len(window.store.profiles) == 1
    assert window.selected()["username"] == "SecondTest"


def test_saved_microsoft_profile_is_unverified_until_real_service_verifies(window, monkeypatch):
    pid = uuid.uuid4().hex
    p = window.store.add_microsoft("ref", "RealPlayer", pid)
    window.refresh()
    assert "VERIFICATION REQUIRED" in window.badge.cget("text")
    assert str(window.launch.cget("state")) == "disabled"
    service = Mock()
    service.verify.return_value = {"name": "RealPlayer", "id": pid}
    monkeypatch.setattr("mcprofiles.ui.AuthService", lambda _: service)
    window.verify_account()
    finish_work(window)
    assert p["id"] in window.verified
    assert str(window.launch.cget("state")) == "normal"
    assert str(window.edit.cget("state")) == "disabled"


def test_failed_reverification_never_opens_launcher(window, monkeypatch):
    p = window.store.add_microsoft("ref", "RealPlayer", uuid.uuid4().hex)
    window.verified.add(p["id"])
    service = Mock()
    service.verify.side_effect = AuthError("Session expired. Sign in again.")
    monkeypatch.setattr("mcprofiles.ui.AuthService", lambda _: service)
    monkeypatch.setattr("mcprofiles.ui.messagebox.showerror", Mock())
    launch = Mock()
    monkeypatch.setattr("mcprofiles.ui.open_official_launcher", launch)
    window.launch_official()
    finish_work(window)
    launch.assert_not_called()
    assert p["id"] not in window.verified
    assert str(window.launch.cget("state")) == "disabled"
