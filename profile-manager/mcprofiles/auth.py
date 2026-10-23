from __future__ import annotations

import threading
import time
import uuid

import keyring
import msal
import requests


class AuthError(Exception):
    """Safe, user-facing failure: never contains provider bodies or credentials."""


def friendly_error(code: str) -> str:
    return {
        "authorization_declined": "Sign-in was declined. Try again when you are ready.",
        "access_denied": "Microsoft denied consent. Check the account and application permissions.",
        "expired_token": "The sign-in code expired. Start sign-in again for a new code.",
        "bad_verification_code": "The sign-in code was not accepted. Start a new sign-in.",
        "invalid_grant": "This session expired or was revoked. Sign in again.",
        "invalid_client": "Check your Microsoft public-client application ID and device-code configuration.",
        "unauthorized_client": "This application is not authorized for this account or Minecraft service.",
        "interaction_required": "Microsoft requires you to sign in again in your browser.",
        "authentication_canceled": "Sign-in was cancelled.",
    }.get(code, "Microsoft sign-in could not complete. Check account consent, app configuration, and network access, then retry.")


class Vault:
    SERVICE = "dev.mcclient.profile-manager"

    def __init__(self):
        backend = keyring.get_keyring()
        module = type(backend).__module__
        # Reject null, plaintext, chained fallbacks and third-party file backends.
        if module not in {"keyring.backends.macOS", "keyring.backends.Windows", "keyring.backends.SecretService"}:
            raise AuthError("A secure OS keychain is unavailable. Enable macOS Keychain, Windows Credential Manager, or Linux Secret Service. Offline profiles remain available.")

    def load(self, client_id):
        try:
            return keyring.get_password(self.SERVICE, client_id)
        except Exception:
            raise AuthError("The OS keychain is locked or unavailable. Unlock it and retry.") from None

    def save(self, client_id, serialized):
        try:
            keyring.set_password(self.SERVICE, client_id, serialized)
        except Exception:
            raise AuthError("Could not store the session securely in the OS keychain. Sign-in was not saved.") from None


class AuthService:
    SCOPES = ["XboxLive.signin"]

    def __init__(self, client_id, vault=None, http=None):
        try:
            uuid.UUID(client_id)
        except (ValueError, TypeError, AttributeError):
            raise AuthError("Set your approved Microsoft public-client application ID in Settings first. No client secret is needed.") from None
        self.vault = vault or Vault()
        self.client_id = client_id
        self.cache = msal.SerializableTokenCache()
        try:
            serialized = self.vault.load(client_id)
            if serialized:
                self.cache.deserialize(serialized)
        except AuthError:
            raise
        except Exception:
            raise AuthError("The saved secure session is unreadable. Clear the application's session in your OS keychain and sign in again.") from None
        self.http = http or requests.Session()
        self.app = msal.PublicClientApplication(client_id, authority="https://login.microsoftonline.com/consumers", token_cache=self.cache, timeout=30)

    def persist(self):
        if self.cache.has_state_changed:
            self.vault.save(self.client_id, self.cache.serialize())

    def begin(self):
        try:
            flow = self.app.initiate_device_flow(scopes=self.SCOPES)
        except requests.RequestException:
            raise AuthError("Cannot reach Microsoft. Check your connection, proxy, and system clock.") from None
        if "user_code" not in flow:
            raise AuthError(friendly_error(flow.get("error", "")))
        # Never follow an arbitrary provider redirect or show tokens in the UI.
        uri = flow.get("verification_uri", "")
        if uri not in {"https://microsoft.com/devicelogin", "https://www.microsoft.com/devicelogin"}:
            raise AuthError("Microsoft returned an unexpected sign-in address. Sign-in was stopped.")
        return flow

    def finish(self, flow, cancelled: threading.Event):
        try:
            deadline = flow.get("expires_at", time.time() + flow.get("expires_in", 900))
            result = self.app.acquire_token_by_device_flow(flow, exit_condition=lambda _: cancelled.is_set() or time.time() >= deadline)
            if cancelled.is_set():
                raise AuthError("Sign-in was cancelled.")
            if time.time() >= deadline:
                raise AuthError(friendly_error("expired_token"))
            if "access_token" not in result:
                raise AuthError(friendly_error(result.get("error", "")))
            identity = self.minecraft_identity(result["access_token"])
            if cancelled.is_set():
                raise AuthError("Sign-in was cancelled.")
            claims = result.get("id_token_claims", {})
            account = next((a for a in self.app.get_accounts()
                            if a.get("local_account_id") == claims.get("oid") or a.get("username") == claims.get("preferred_username")), None)
            if not account:
                raise AuthError("Microsoft account reference was missing. Sign in again.")
            self.persist()
            return account["home_account_id"], identity
        except requests.RequestException:
            raise AuthError("Sign-in could not reach Microsoft or Minecraft services. Check your network and retry.") from None

    def verify(self, account_id):
        try:
            account = next((a for a in self.app.get_accounts() if a["home_account_id"] == account_id), None)
            if not account:
                raise AuthError("No saved session for this account. Use Microsoft sign-in again.")
            result = self.app.acquire_token_silent(self.SCOPES, account=account)
            if not result or "access_token" not in result:
                raise AuthError(friendly_error((result or {}).get("error", "interaction_required")))
            identity = self.minecraft_identity(result["access_token"])
            self.persist()
            return identity
        except requests.RequestException:
            raise AuthError("Cannot verify this account right now. Check your connection and retry; offline access was not substituted.") from None

    def forget(self, account_id):
        for account in self.app.get_accounts():
            if account["home_account_id"] == account_id:
                self.app.remove_account(account)
        self.persist()

    def _json(self, response, service):
        if response.status_code == 429:
            raise AuthError(f"{service} is rate limiting requests. Wait a few minutes before retrying.")
        if response.status_code >= 500:
            raise AuthError(f"{service} is temporarily unavailable. Try again later.")
        if not response.ok:
            raise AuthError(f"{service} rejected sign-in. Check account permissions and your approved application registration.")
        try:
            return response.json()
        except ValueError:
            raise AuthError(f"{service} returned an unexpected response. Try again later.") from None

    def minecraft_identity(self, microsoft_token):
        """Official Microsoft -> Xbox -> XSTS -> Minecraft exchange, with ownership checks."""
        try:
            xbox = self._json(self.http.post("https://user.auth.xboxlive.com/user/authenticate", timeout=30, json={
                "Properties": {"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": "d=" + microsoft_token},
                "RelyingParty": "http://auth.xboxlive.com", "TokenType": "JWT"}), "Xbox Live")
            response = self.http.post("https://xsts.auth.xboxlive.com/xsts/authorize", timeout=30, json={
                "Properties": {"SandboxId": "RETAIL", "UserTokens": [xbox["Token"]]},
                "RelyingParty": "rp://api.minecraftservices.com/", "TokenType": "JWT"})
            if response.status_code == 401:
                try:
                    code = response.json().get("XErr")
                except ValueError:
                    code = None
                raise AuthError({2148916233: "Create an Xbox profile for this Microsoft account, then retry.",
                                 2148916238: "This child account needs family-organizer approval and Xbox privacy settings."}.get(code,
                                "Xbox account restrictions prevented sign-in. Check family, region, and privacy settings."))
            xsts = self._json(response, "Xbox authorization")
            uhs = xsts["DisplayClaims"]["xui"][0]["uhs"]
            xbox_uhs = xbox["DisplayClaims"]["xui"][0]["uhs"]
            if uhs != xbox_uhs:
                raise AuthError("Xbox identity verification failed. Sign-in was stopped.")
            minecraft = self._json(self.http.post("https://api.minecraftservices.com/authentication/login_with_xbox", timeout=30,
                                                  json={"identityToken": f"XBL3.0 x={uhs};{xsts['Token']}"}), "Minecraft authentication")
            headers = {"Authorization": "Bearer " + minecraft["access_token"]}
            entitlements = self._json(self.http.get("https://api.minecraftservices.com/entitlements/mcstore", timeout=30, headers=headers), "Minecraft ownership")
            if not entitlements.get("items"):
                raise AuthError("This account has no Minecraft entitlement. Use an account with legitimate Java Edition access.")
            profile_response = self.http.get("https://api.minecraftservices.com/minecraft/profile", timeout=30, headers=headers)
            if profile_response.status_code == 404:
                raise AuthError("No Java Edition profile exists. Complete Minecraft account setup or check your Java Edition access.")
            identity = self._json(profile_response, "Minecraft profile")
            uuid.UUID(identity["id"])
            if not identity.get("name"):
                raise ValueError("Missing name")
            return {"name": identity["name"], "id": identity["id"]}
        except (KeyError, IndexError, TypeError, ValueError):
            raise AuthError("An account service returned incomplete identity data. Sign-in was stopped; retry later.") from None
