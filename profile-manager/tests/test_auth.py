from unittest.mock import Mock
import threading

import pytest

from mcprofiles.auth import AuthService, AuthError, Vault, friendly_error


def response(data, status=200):
    return Mock(status_code=status, ok=status < 400, json=Mock(return_value=data))


def service_with_http():
    service = AuthService.__new__(AuthService)
    service.http = Mock()
    service.http.post.side_effect = [
        response({"Token": "xbox-test", "DisplayClaims": {"xui": [{"uhs": "same-user"}]}}),
        response({"Token": "xsts-test", "DisplayClaims": {"xui": [{"uhs": "same-user"}]}}),
        response({"access_token": "minecraft-test"}),
    ]
    service.http.get.side_effect = [
        response({"items": [{"name": "game_minecraft"}]}),
        response({"name": "RealPlayer", "id": "12345678123456781234567812345678"}),
    ]
    return service


def test_official_service_exchange_and_owned_minecraft_profile():
    service = service_with_http()
    identity = service.minecraft_identity("microsoft-test")
    assert identity["name"] == "RealPlayer"
    assert service.http.post.call_args_list[0].kwargs["json"]["Properties"]["RpsTicket"] == "d=microsoft-test"
    calls = service.http.post.call_args_list + service.http.get.call_args_list
    assert all(c.args[0].startswith("https://") for c in calls)
    assert all(c.kwargs["timeout"] == 30 for c in calls)
    assert not any("verify" in c.kwargs for c in calls)  # default TLS verification remains enabled


def test_no_ownership_no_profile_or_offline_fallback():
    service = service_with_http()
    service.http.get.side_effect = [response({"items": []})]
    with pytest.raises(AuthError, match="no Minecraft entitlement"):
        service.minecraft_identity("microsoft-test")
    assert service.http.get.call_count == 1


def test_missing_java_profile_cannot_authenticate():
    service = service_with_http()
    service.http.get.side_effect = [response({"items": [{"name": "game_minecraft"}]}), response({}, 404)]
    with pytest.raises(AuthError, match="No Java Edition profile"):
        service.minecraft_identity("microsoft-test")


@pytest.mark.parametrize("code, message", [(2148916233, "Xbox profile"), (2148916238, "family-organizer")])
def test_xbox_restrictions_produce_helpful_errors(code, message):
    service = service_with_http()
    service.http.post.side_effect = [response({"Token": "xbox-test"}), response({"XErr": code}, 401)]
    with pytest.raises(AuthError, match=message):
        service.minecraft_identity("microsoft-test")


def test_xbox_identity_mismatch_stops_exchange():
    service = service_with_http()
    service.http.post.side_effect = [
        response({"Token": "xbox-test", "DisplayClaims": {"xui": [{"uhs": "one"}]}}),
        response({"Token": "xsts-test", "DisplayClaims": {"xui": [{"uhs": "another"}]}}),
    ]
    with pytest.raises(AuthError, match="identity verification failed"):
        service.minecraft_identity("microsoft-test")
    assert service.http.post.call_count == 2


def test_vault_rejects_insecure_backend(monkeypatch):
    monkeypatch.setattr("keyring.get_keyring", lambda: Mock())
    with pytest.raises(AuthError, match="secure OS keychain"):
        Vault()


def test_microsoft_failures_do_not_display_provider_secret_fields():
    service = AuthService.__new__(AuthService)
    service.app = Mock()
    service.app.acquire_token_by_device_flow.return_value = {"error": "invalid_grant", "error_description": "secret-token-must-not-leak"}
    with pytest.raises(AuthError) as failure:
        service.finish({}, threading.Event())
    assert "secret-token" not in str(failure.value)
    assert "sign in again" in str(failure.value).lower()


def test_device_code_cannot_redirect_to_non_microsoft_site():
    service = AuthService.__new__(AuthService)
    service.app = Mock()
    service.app.initiate_device_flow.return_value = {"user_code": "TEST", "verification_uri": "https://example.com/login"}
    with pytest.raises(AuthError, match="unexpected sign-in address"):
        service.begin()


def test_expired_code_has_actionable_message():
    assert "new code" in friendly_error("expired_token")


def test_cancelled_signin_does_not_verify_or_persist():
    service = AuthService.__new__(AuthService)
    service.app = Mock()
    service.app.acquire_token_by_device_flow.return_value = {"access_token": "test"}
    service.minecraft_identity = Mock()
    cancelled = threading.Event()
    cancelled.set()
    with pytest.raises(AuthError, match="cancelled"):
        service.finish({}, cancelled)
    service.minecraft_identity.assert_not_called()


def test_missing_app_id_is_actionable():
    with pytest.raises(AuthError, match="approved Microsoft"):
        AuthService("")


def test_device_polling_stops_at_expiry():
    service = AuthService.__new__(AuthService)
    service.app = Mock()
    def expired(flow, exit_condition):
        assert exit_condition(flow)
        return {"error": "authorization_pending"}
    service.app.acquire_token_by_device_flow.side_effect = expired
    with pytest.raises(AuthError, match="code expired"):
        service.finish({"expires_at": 0}, threading.Event())


def test_cancel_during_ownership_check_does_not_save_session():
    service = AuthService.__new__(AuthService)
    service.app = Mock()
    service.app.acquire_token_by_device_flow.return_value = {"access_token": "test"}
    service.persist = Mock()
    cancelled = threading.Event()
    def identity(_):
        cancelled.set()
        return {"name": "RealPlayer", "id": "0" * 32}
    service.minecraft_identity = identity
    with pytest.raises(AuthError, match="cancelled"):
        service.finish({}, cancelled)
    service.persist.assert_not_called()
