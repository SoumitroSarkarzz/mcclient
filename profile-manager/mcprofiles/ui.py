from __future__ import annotations

import os
from pathlib import Path
import queue
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import ttk, messagebox, simpledialog, filedialog
import uuid
import webbrowser

from .auth import AuthError, AuthService
from .model import Store, ProfileError, official_launch_allowed


def settings_directory():
    if sys.platform == "darwin":
        return Path.home() / "Library/Application Support/MCProfiles"
    if sys.platform == "win32":
        return Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData/Local")) / "MCProfiles"
    return Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "mcprofiles"


def open_official_launcher():
    if sys.platform == "darwin":
        for app in [Path("/Applications/Minecraft.app"), Path.home() / "Applications/Minecraft.app"]:
            if app.exists():
                subprocess.Popen(["/usr/bin/open", str(app)])
                return
    webbrowser.open("https://www.minecraft.net/download")


class Window:
    def __init__(self, root, store):
        self.root, self.store = root, store
        self.verified = set()
        self.events = queue.Queue()
        self.cancel = threading.Event()
        self.busy = False
        self.cancellable = False
        self.closed = False
        self.buttons = []
        root.title("MC Profiles")
        root.geometry("960x730")
        root.minsize(880, 700)
        root.configure(bg="#111827")
        style = ttk.Style(root)
        style.theme_use("clam")
        style.configure("TFrame", background="#111827")
        style.configure("TLabel", background="#111827", foreground="#e5e7eb", font=("Helvetica", 11))
        style.configure("Title.TLabel", font=("Helvetica", 24, "bold"))
        style.configure("Muted.TLabel", foreground="#a8b4c8")
        style.configure("Badge.TLabel", foreground="#67e8f9", font=("Helvetica", 12, "bold"))
        style.configure("TButton", padding=(12, 9), font=("Helvetica", 11), background="#26364a", foreground="#e5e7eb")
        style.map("TButton", background=[("active", "#155e75"), ("disabled", "#1b2636")],
                  foreground=[("disabled", "#68758a")])
        outer = ttk.Frame(root, padding=24)
        outer.pack(fill="both", expand=True)
        ttk.Label(outer, text="MC Profiles", style="Title.TLabel").pack(anchor="w")
        ttk.Label(outer, text="Your accounts. Your local test profiles. Clearly separated.", style="Muted.TLabel").pack(anchor="w", pady=(4, 20))
        body = ttk.Frame(outer)
        body.pack(fill="both", expand=True)
        sidebar = ttk.Frame(body)
        sidebar.pack(side="left", fill="y", padx=(0, 24))
        self.listbox = tk.Listbox(sidebar, width=30, bg="#1f2937", fg="#e5e7eb", selectbackground="#155e75",
                                  selectforeground="white", borderwidth=0, highlightthickness=0, font=("Helvetica", 12), exportselection=False)
        self.listbox.pack(fill="both", expand=True)
        self.listbox.bind("<<ListboxSelect>>", self.selection)
        self.button(sidebar, "+ Offline/test profile", self.add_offline).pack(fill="x", pady=(12, 6))
        self.button(sidebar, "+ Microsoft sign-in", self.sign_in).pack(fill="x")
        detail = ttk.Frame(body)
        detail.pack(side="left", fill="both", expand=True)
        self.title = ttk.Label(detail, text="Choose a profile", font=("Helvetica", 18, "bold"))
        self.title.pack(anchor="w", pady=(4, 12))
        self.badge = ttk.Label(detail, style="Badge.TLabel")
        self.badge.pack(anchor="w")
        self.description = ttk.Label(detail, wraplength=420, justify="left")
        self.description.pack(anchor="w", pady=(12, 18))
        actions = ttk.Frame(detail)
        actions.pack(anchor="w")
        self.button(actions, "Rename label", self.rename).grid(row=0, column=0, padx=(0, 6), pady=4)
        self.button(actions, "Delete profile", self.delete).grid(row=0, column=1, pady=4)
        self.edit = self.button(detail, "Edit test username", self.edit_username)
        self.edit.pack(anchor="w", pady=(8, 4))
        self.export = self.button(detail, "Export local test fixture", self.export_fixture)
        self.export.pack(anchor="w", pady=4)
        self.verify = self.button(detail, "Verify Microsoft account", self.verify_account)
        self.verify.pack(anchor="w", pady=4)
        self.launch = self.button(detail, "Open official Minecraft Launcher", self.launch_official)
        self.launch.pack(anchor="w", pady=4)
        self.status = ttk.Label(outer, text="Ready", wraplength=810, style="Muted.TLabel")
        self.status.pack(anchor="w", pady=(18, 10))
        footer = ttk.Frame(outer)
        footer.pack(fill="x")
        self.button(footer, "Sign-in settings", self.settings).pack(side="left")
        self.button(footer, "Get official launcher", lambda: webbrowser.open("https://www.minecraft.net/download")).pack(side="left", padx=8)
        self.cancel_button = ttk.Button(footer, text="Cancel sign-in", command=self.cancel.set, state="disabled")
        self.cancel_button.pack(side="right")
        root.protocol("WM_DELETE_WINDOW", self.close)
        self.refresh()
        root.after(100, self.poll)

    def button(self, parent, title, action):
        def safe():
            try:
                action()
            except (ProfileError, AuthError, OSError) as error:
                messagebox.showerror("MC Profiles", str(error), parent=self.root)
        b = ttk.Button(parent, text=title, command=safe)
        self.buttons.append(b)
        return b

    def selected(self):
        return self.store.get(self.store.data["selected"])

    def refresh(self):
        self.listbox.delete(0, tk.END)
        for i, p in enumerate(self.store.profiles):
            marker = "TEST" if p["kind"] == "offline" else "MS"
            self.listbox.insert(tk.END, f"[{marker}] {p['label']}")
            if p["id"] == self.store.data["selected"]:
                self.listbox.selection_set(i)
        for b in self.buttons:
            b.configure(state="disabled" if self.busy else "normal")
        self.listbox.configure(state="disabled" if self.busy else "normal")
        self.cancel_button.configure(state="normal" if self.busy and self.cancellable else "disabled")
        self.show_profile()

    def show_profile(self):
        for b in [self.edit, self.export, self.verify, self.launch]:
            b.configure(state="disabled")
        try:
            p = self.selected()
        except ProfileError:
            self.title.configure(text="Choose a profile")
            self.badge.configure(text="No profile selected")
            self.description.configure(text="Add an offline test profile or sign in with your Microsoft account. Passwords are entered only on Microsoft's website.")
            return
        self.title.configure(text=p["label"])
        if p["kind"] == "offline":
            self.badge.configure(text="OFFLINE / LOCAL TEST ONLY")
            self.description.configure(text=f"Test username: {p['username']}\n\nNot authenticated. This profile is local fixture data only. It cannot launch Minecraft or connect to a server through this manager.")
            if not self.busy:
                self.edit.configure(state="normal")
                self.export.configure(state="normal")
        else:
            valid = p["id"] in self.verified
            self.badge.configure(text="AUTHENTICATED · VERIFIED THIS SESSION" if valid else "MICROSOFT · VERIFICATION REQUIRED")
            self.description.configure(text=f"Minecraft player: {p['username']}\n\n{'Account and Minecraft entitlement verified.' if valid else 'Saved profile metadata is not proof of authentication.'}\nThe official launcher performs its own sign-in and session checks. Renaming this label does not change your Minecraft identity.")
            if not self.busy:
                self.verify.configure(state="normal")
                self.launch.configure(state="normal" if official_launch_allowed(p, self.verified) else "disabled")

    def selection(self, _event):
        indices = self.listbox.curselection()
        if indices and not self.busy:
            try:
                self.store.select(self.store.profiles[indices[0]]["id"])
                self.show_profile()
            except (ProfileError, OSError) as error:
                messagebox.showerror("Settings", str(error), parent=self.root)

    def add_offline(self):
        player = simpledialog.askstring("Offline test profile", "Custom test username (3–16 letters, numbers, underscores):", parent=self.root)
        if player is not None:
            self.store.add_offline(player, player)
            self.refresh()

    def rename(self):
        p = self.selected()
        value = simpledialog.askstring("Rename label", "Local profile label:", initialvalue=p["label"], parent=self.root)
        if value is not None:
            self.store.rename(p["id"], value)
            self.refresh()

    def edit_username(self):
        p = self.selected()
        if p["kind"] != "offline":
            raise ProfileError("Only offline test usernames can be edited.")
        value = simpledialog.askstring("Test username", "Local test username:", initialvalue=p["username"], parent=self.root)
        if value is not None:
            self.store.set_username(p["id"], value)
            self.refresh()

    def delete(self):
        p = self.selected()
        if not messagebox.askyesno("Delete profile", f"Delete '{p['label']}'? Saved Microsoft sessions for this profile will also be removed from this app. This does not delete your Minecraft account.", parent=self.root):
            return
        def finish(_):
            self.verified.discard(p["id"])
            self.store.delete(p["id"])
            self.refresh()
        if p["kind"] == "microsoft":
            self.work(lambda: AuthService(self.store.data["client_id"]).forget(p["account_id"]), finish)
        else:
            finish(None)

    def export_fixture(self):
        p = self.selected()
        if p["kind"] != "offline":
            raise ProfileError("Only offline test profiles support fixture export.")
        filename = filedialog.asksaveasfilename(parent=self.root, title="Export local fixture (not a game session)", initialfile="offline-test-profile.json", defaultextension=".json", filetypes=[("JSON", "*.json")])
        if filename:
            if Path(filename).resolve() == self.store.path.resolve():
                raise ProfileError("Choose a fixture path outside the app settings file.")
            self.store.export_test_fixture(p["id"], Path(filename))
            self.status.configure(text="Local fixture exported. It contains no credentials and grants no game or server access.")

    def settings(self):
        value = simpledialog.askstring("Microsoft application", "Your approved public-client application ID (not a password or client secret):", initialvalue=self.store.data["client_id"], parent=self.root)
        if value is None:
            return
        value = value.strip()
        if value:
            try:
                uuid.UUID(value)
            except ValueError:
                raise ProfileError("An application ID must be a UUID, not an email, password, or client secret.") from None
        if self.store.profiles and any(p["kind"] == "microsoft" for p in self.store.profiles) and value != self.store.data["client_id"]:
            raise ProfileError("Delete Microsoft profiles first so their old secure sessions can be removed before changing the application ID.")
        self.store.data["client_id"] = value
        self.store.save()
        self.verified.clear()
        self.refresh()

    def sign_in(self):
        client_id = self.store.data["client_id"]
        if not client_id:
            raise AuthError("Set an approved Microsoft application ID in Sign-in settings, or use the official launcher. TLauncher does not provide this app registration.")
        def job():
            service = AuthService(client_id)
            flow = service.begin()
            self.events.put(("device", flow))
            return service.finish(flow, self.cancel)
        def done(result):
            account, identity = result
            p = self.store.add_microsoft(account, identity["name"], identity["id"])
            self.verified.add(p["id"])
            self.status.configure(text="Minecraft account and entitlement verified. Tokens are stored only in the OS keychain.")
            self.refresh()
        self.work(job, done, cancellable=True)

    def verify_account(self, launch=False):
        p = self.selected()
        if p["kind"] != "microsoft":
            raise ProfileError("Offline profiles cannot be used for authenticated Minecraft access.")
        self.verified.discard(p["id"])
        def done(identity):
            if identity["id"] != p["player_id"]:
                raise AuthError("Verified account identity changed. Delete the saved profile and sign in again.")
            p["username"] = identity["name"]
            self.store.save()
            self.verified.add(p["id"])
            self.status.configure(text="Account verified. The official launcher still requires its normal Microsoft session.")
            self.refresh()
            if launch:
                open_official_launcher()
        self.work(lambda: AuthService(self.store.data["client_id"]).verify(p["account_id"]), done)

    def launch_official(self):
        if not official_launch_allowed(self.selected(), self.verified):
            raise ProfileError("Verify a Microsoft profile first. Offline profiles cannot launch multiplayer.")
        self.verify_account(launch=True)

    def work(self, job, done, cancellable=False):
        if self.busy:
            return
        self.busy = True
        self.cancellable = cancellable
        self.cancel.clear()
        self.status.configure(text="Contacting account services…")
        self.refresh()
        def worker():
            try:
                result = job()
                self.events.put(("done", (done, result)))
            except (AuthError, ProfileError) as error:
                self.events.put(("error", str(error)))
            except Exception:
                # Provider exceptions may contain URLs, bodies or tokens; never display them.
                self.events.put(("error", "Operation failed. Check your network, approved application configuration, and secure keychain, then retry."))
        threading.Thread(target=worker, daemon=True).start()

    def poll(self):
        if self.closed:
            return
        try:
            while True:
                kind, payload = self.events.get_nowait()
                if kind == "device":
                    self.status.configure(text=f"Microsoft sign-in code: {payload['user_code']}\nOpen microsoft.com/devicelogin, enter this code, and approve only this app. Never share the code. It expires in {payload.get('expires_in', 900)} seconds.")
                    webbrowser.open(payload["verification_uri"])
                else:
                    self.busy = False
                    self.refresh()
                    if kind == "error":
                        self.status.configure(text=payload)
                        messagebox.showerror("Sign-in", payload, parent=self.root)
                    else:
                        done, result = payload
                        try:
                            done(result)
                        except (AuthError, ProfileError, OSError) as error:
                            messagebox.showerror("MC Profiles", str(error), parent=self.root)
        except queue.Empty:
            pass
        self.root.after(100, self.poll)

    def close(self):
        self.closed = True
        self.cancel.set()
        self.root.destroy()


def main():
    root = tk.Tk()
    try:
        store = Store(settings_directory())
    except ProfileError as error:
        root.withdraw()
        messagebox.showerror("Settings error", str(error), parent=root)
        root.destroy()
        return
    Window(root, store)
    root.mainloop()


if __name__ == "__main__":
    main()
