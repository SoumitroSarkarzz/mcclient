# MC Profiles

A standalone desktop **profile manager**, separate from the Fabric client mod. It manages local test data and verifies legitimate Microsoft/Minecraft accounts. It does not install Minecraft, rewrite launcher account files, inject sessions into TLauncher, or automatically switch accounts inside another launcher.

## Run on your Mac

Install Python 3.11 or later from [python.org](https://www.python.org/downloads/macos/) with Tk support, download this repository, and open Terminal in its `profile-manager` directory:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install .
.venv/bin/mcprofiles
```

Alternatively, double-click `Run on Mac.command` after downloading the source. It prepares a local virtual environment and installs the package on first use. A Python virtual environment is not a Minecraft game instance. This app is **not** a JAR and does not go in `mods`.

## Profiles

- **Offline / local test only:** add a custom 3–16 character username, rename the local label, edit the test username, switch selection, delete, or export a JSON fixture for your own local tests. These profiles cannot launch a game or connect to any server through this manager. Fixtures contain no official account UUID, game arguments, session, or credentials. The manager does not claim an offline name belongs to a Minecraft account.
- **Microsoft:** sign in through Microsoft's official device-code website. This app never asks for a Microsoft password. It checks Xbox/XSTS authorization, Minecraft entitlement, and the Java Edition profile before adding a verified account. Account identity comes from Minecraft's service; the local label can be renamed but the Minecraft username cannot be edited here.
- **Switching:** selecting a row makes it the active local profile and persists that selection. Saved Microsoft metadata is never sufficient for an authenticated badge. Each app restart requires fresh service verification; opening the official launcher also rechecks the account.
- **Launching:** verified Microsoft profiles can open the official Minecraft Launcher on macOS if installed, otherwise the official download page. The official launcher performs its own normal sign-in, ownership and session validation. Select the same legitimate account inside that launcher yourself. No token is transferred to it or to TLauncher.

## Microsoft application registration is required

In **Sign-in settings**, enter your own Microsoft public-client **application ID** (a UUID). Never enter an email, password, client secret, or someone else's application ID. The application must support personal Microsoft accounts and device-code/public-client sign-in, and must have the required approval/access for Minecraft services. An Entra registration alone does not guarantee Minecraft API access. Follow the current Microsoft and Minecraft developer requirements for your application.

TLauncher is not an application registration for this manager. Until an approved ID is configured, local profiles work, and **Get official launcher** provides a handoff to the official launcher. In-app Microsoft authentication is unavailable without that registration. This repository intentionally ships no borrowed client ID or secret.

Sign-in opens `https://microsoft.com/devicelogin` and shows a short-lived code. Approve only your own app and do not share the code. Cancellation and expiry produce actionable messages. Xbox family restrictions, missing Java profiles/entitlements, revoked sessions, network failures, and locked keychains also have specific guidance. Raw provider responses and tokens are never shown in errors or logs.

## Storage and security

Non-secret profile labels, last verified player names/IDs, selection, and public application ID are stored in:

- macOS: `~/Library/Application Support/MCProfiles/profiles.json`
- Windows: `%LOCALAPPDATA%/MCProfiles/profiles.json`
- Linux: `$XDG_CONFIG_HOME/mcprofiles/profiles.json` or `~/.config/mcprofiles/profiles.json`

Writes use atomic replacement with owner-only file permissions (0600 on POSIX) and an owner-only app directory when created (0700). Settings are not encrypted because they contain no passwords or tokens; anyone with access to your OS account can still see profile metadata. On Windows, OS user-directory ACLs supply the primary access boundary. Corrupt settings are reported without overwriting the file. Back up the file before repairing it.

The MSAL token cache goes exclusively into macOS Keychain, Windows Credential Manager, or Linux Secret Service through `keyring`. Plaintext, null and third-party file keyring backends are rejected. Without a secure keychain, Microsoft sign-in stops and offline profiles continue working. Minecraft access tokens exist only in memory during verification and are not exported. Deleting a Microsoft profile removes this app's cached account session first; deletion stops if the keychain cannot be updated. It does not delete the Microsoft/Minecraft account or revoke independent launcher sessions. Sign out of the official launcher separately when needed.

The app uses the normal TLS-verified Microsoft → Xbox Live → XSTS → Minecraft flow. It does not bypass authentication, entitlement checks, session validation or server security, and it does not impersonate another account. It exposes no arbitrary game-launch command or offline server connection capability.

## Development and validation

```sh
.venv/bin/python -m pip install 'pytest>=8,<9'
.venv/bin/python -m pytest -q
```

Tests cover profile lifecycle/persistence, validation, permissions, untrusted saved metadata, offline restrictions, identity mismatch, ownership rejection, Microsoft error handling and keychain requirements. OAuth/network tests use simulated service responses. On Linux without a display the three desktop interaction tests skip; use a desktop or Xvfb to execute them. Tests do not need or contain real credentials.

Current validation: 30 tests passed with an Xvfb display, and the Tk interface was opened and visually inspected. **Live Microsoft authentication and macOS Keychain have not been verified** because no approved application registration or user consent was provided. No multiplayer session was launched or tested.
