from __future__ import annotations

import json
import os
from pathlib import Path
import re
import tempfile
import uuid


class ProfileError(ValueError):
    pass


def label(value: str) -> str:
    value = value.strip()
    if not 1 <= len(value) <= 60 or any(ord(c) < 32 for c in value):
        raise ProfileError("Use a profile label of 1–60 characters without control characters.")
    return value


def username(value: str) -> str:
    value = value.strip()
    if not re.fullmatch(r"[A-Za-z0-9_]{3,16}", value):
        raise ProfileError("Test usernames need 3–16 letters, numbers, or underscores.")
    return value


def atomic_private_json(path: Path, data: dict) -> None:
    path = Path(path)
    if path.parent.is_symlink() or path.is_symlink():
        raise ProfileError("Settings and test fixtures cannot use symbolic links.")
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd, temporary = tempfile.mkstemp(prefix=".mcprofiles-", dir=path.parent)
    try:
        if hasattr(os, "fchmod"):
            os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(data, stream, indent=2)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


class Store:
    """Non-secret metadata only; authentication is never inferred from this file."""

    def __init__(self, directory: Path):
        self.path = directory / "profiles.json"
        self.data = {"schema": 1, "client_id": "", "selected": None, "profiles": []}
        if self.path.exists():
            try:
                raw = json.loads(self.path.read_text(encoding="utf-8"))
                self._validate(raw)
                self.data = raw
            except (ValueError, TypeError, KeyError, AttributeError, OSError) as error:
                raise ProfileError("Cannot read settings. Back up profiles.json before repairing it; it was not overwritten.") from error

    @staticmethod
    def _validate(data):
        if not isinstance(data, dict):
            raise ProfileError("Unsupported settings format")
        if data.get("schema") != 1 or not isinstance(data.get("profiles"), list):
            raise ProfileError("Unsupported settings format")
        client = data.get("client_id", "")
        if client:
            uuid.UUID(client)
        ids = set()
        for profile in data["profiles"]:
            uuid.UUID(profile["id"])
            if profile["id"] in ids:
                raise ProfileError("Duplicate profile")
            ids.add(profile["id"])
            label(profile["label"])
            if profile["kind"] == "offline":
                username(profile["username"])
            elif profile["kind"] == "microsoft":
                if not isinstance(profile.get("account_id"), str) or not profile["account_id"]:
                    raise ProfileError("Missing account reference")
            else:
                raise ProfileError("Unknown profile type")
        if data.get("selected") is not None and data["selected"] not in ids:
            raise ProfileError("Unknown selected profile")

    @property
    def profiles(self):
        return self.data["profiles"]

    def save(self):
        self._validate(self.data)
        atomic_private_json(self.path, self.data)

    def get(self, profile_id):
        for profile in self.profiles:
            if profile["id"] == profile_id:
                return profile
        raise ProfileError("Select a profile first.")

    def add_offline(self, name, player):
        p = {"id": str(uuid.uuid4()), "kind": "offline", "label": label(name), "username": username(player)}
        self.profiles.append(p)
        self.select(p["id"])
        return p

    def add_microsoft(self, account_id, verified_name, player_id):
        existing = next((p for p in self.profiles if p.get("account_id") == account_id), None)
        if existing:
            existing.update(username=verified_name, player_id=player_id)
            self.select(existing["id"])
            return existing
        p = {"id": str(uuid.uuid4()), "kind": "microsoft", "label": label(verified_name),
             "username": verified_name, "player_id": player_id, "account_id": account_id}
        self.profiles.append(p)
        self.select(p["id"])
        return p

    def select(self, profile_id):
        self.get(profile_id)
        self.data["selected"] = profile_id
        self.save()

    def rename(self, profile_id, name):
        self.get(profile_id)["label"] = label(name)
        self.save()

    def set_username(self, profile_id, player):
        p = self.get(profile_id)
        if p["kind"] != "offline":
            raise ProfileError("Microsoft player names come only from the verified Minecraft account.")
        p["username"] = username(player)
        self.save()

    def delete(self, profile_id):
        self.get(profile_id)
        self.data["profiles"] = [p for p in self.profiles if p["id"] != profile_id]
        if self.data["selected"] == profile_id:
            self.data["selected"] = self.profiles[0]["id"] if self.profiles else None
        self.save()

    def export_test_fixture(self, profile_id, destination: Path):
        p = self.get(profile_id)
        if p["kind"] != "offline":
            raise ProfileError("Only offline profiles can be exported as test fixtures.")
        # Data fixture only: never game arguments, session IDs, official UUIDs, or credentials.
        atomic_private_json(destination, {"schema": 1, "mode": "local-test-only", "username": p["username"],
                                         "local_profile_id": p["id"], "authenticated": False})


def official_launch_allowed(profile: dict, verified_ids: set[str]) -> bool:
    return profile["kind"] == "microsoft" and profile["id"] in verified_ids
