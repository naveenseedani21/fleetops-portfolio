import io
import json
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs

import pytest
import fleetops_sim as sim


def response(data):
    return io.BytesIO(json.dumps(data).encode())


@pytest.fixture
def clock(monkeypatch):
    sleeps = []
    monkeypatch.setattr(sim.time, "sleep", sleeps.append)
    monkeypatch.setattr(sim.random, "uniform", lambda a, b: 0)
    return sleeps


def test_timeouts_token_cache_and_authenticated_body(monkeypatch, clock):
    calls = []
    def send(request, timeout):
        calls.append((request, timeout))
        return response({"access_token": "machine-token", "expires_in": 300} if len(calls) == 1 else {"revision": 2})
    monkeypatch.setattr(sim, "urlopen", send)
    client = sim.Client("http://localhost:8080", "http://localhost:8180/realm", "team", "client-secret", 0.25)
    for _ in range(2):
        assert client.request("POST", "devices/id/reports", {"reportId": "same"}) == {"revision": 2}
    assert len(calls) == 3
    assert all(timeout == 15 for _, timeout in calls)
    assert parse_qs(calls[0][0].data.decode())["grant_type"] == ["client_credentials"]
    assert calls[1][0].get_header("Authorization") == "Bearer machine-token"
    assert json.loads(calls[1][0].data) == {"reportId": "same"}
    assert clock == [0.25, 0.25, 0.25]


@pytest.mark.parametrize("code", [429, 502, 503, 504])
def test_transient_retry_preserves_exact_request(monkeypatch, clock, code):
    requests = []
    def send(request, timeout):
        requests.append(request)
        if len(requests) < 3:
            raise HTTPError(request.full_url, code, "secret diagnostic", {}, None)
        return response({"ok": True})
    monkeypatch.setattr(sim, "urlopen", send)
    client = sim.Client("http://localhost", "http://localhost/realm", "team", None, 0.25, admin_token="admin-token")
    assert client.request("POST", "devices", {"serial": "SYN-1"}) == {"ok": True}
    assert len(requests) == 3 and requests[0] is requests[1] is requests[2]
    assert clock == [0.25, 1, 0.25, 2, 0.25]


@pytest.mark.parametrize("failure", [URLError("private detail"), TimeoutError("private detail"), ConnectionResetError("private detail")])
def test_network_retries_are_bounded_and_redacted(monkeypatch, clock, failure):
    calls = []
    def send(request, timeout):
        calls.append(request)
        raise failure
    monkeypatch.setattr(sim, "urlopen", send)
    client = sim.Client("http://localhost", "http://localhost/realm", "team", "private-secret", 0.25)
    with pytest.raises(RuntimeError, match="Identity service request failed") as error:
        client.request("POST", "devices/id/reports", {})
    assert len(calls) == 3
    assert "private" not in str(error.value)


@pytest.mark.parametrize("code", [400, 401, 403, 404, 409, 422])
def test_validation_and_authorization_errors_do_not_retry(monkeypatch, clock, code):
    calls = []
    def send(request, timeout):
        calls.append(request)
        raise HTTPError(request.full_url, code, "sensitive response", {}, None)
    monkeypatch.setattr(sim, "urlopen", send)
    client = sim.Client("http://localhost", "http://localhost/realm", "team", None, 0.25, admin_token="private-token")
    with pytest.raises(RuntimeError, match=f"HTTP {code}") as error:
        client.request("POST", "devices", {})
    assert len(calls) == 1
    assert "sensitive" not in str(error.value) and "private-token" not in str(error.value)


def test_authentication_failure_is_redacted_and_not_retried(monkeypatch, clock):
    calls = []
    def send(request, timeout):
        calls.append(request)
        raise HTTPError(request.full_url, 401, "client-secret-in-diagnostic", {}, None)
    monkeypatch.setattr(sim, "urlopen", send)
    client = sim.Client("http://localhost", "http://localhost/realm", "team", "client-secret", 0.25)
    with pytest.raises(RuntimeError, match="Identity service returned HTTP 401") as error:
        client.request("POST", "devices/id/reports", {})
    assert len(calls) == 1 and "client-secret" not in str(error.value)


def test_admin_provisioning_never_requests_machine_token(monkeypatch, clock):
    calls = []
    def send(request, timeout):
        calls.append(request)
        return response({"id": "registered"})
    monkeypatch.setattr(sim, "urlopen", send)
    client = sim.Client("http://localhost", "http://localhost/realm", "team", None, 0.25, admin_token="admin-token")
    client.request("POST", "devices", {})
    assert len(calls) == 1
    assert calls[0].get_header("Authorization") == "Bearer admin-token"


def test_invalid_identity_response_is_safe(monkeypatch, clock):
    monkeypatch.setattr(sim, "urlopen", lambda request, timeout: response({"access_token": "private", "expires_in": "bad"}))
    with pytest.raises(RuntimeError, match="invalid token response") as error:
        sim.Client("http://localhost", "http://localhost/realm", "team", "secret", 0.25).request("POST", "devices/id/reports", {})
    assert "private" not in str(error.value)


def test_expired_tokens_are_refreshed_before_next_request(monkeypatch, clock):
    now = [100]
    tokens = []
    monkeypatch.setattr(sim.time, "monotonic", lambda: now[0])
    def send(request, timeout):
        if request.full_url.endswith('/token'):
            tokens.append(len(tokens) + 1)
            return response({"access_token": f"token-{len(tokens)}", "expires_in": 60})
        return response({"authorization": request.get_header("Authorization")})
    monkeypatch.setattr(sim, "urlopen", send)
    client = sim.Client("http://localhost", "http://localhost/realm", "team", "secret", 0.25)
    assert client.request("POST", "devices/id/reports", {})['authorization'] == 'Bearer token-1'
    now[0] = 131
    assert client.request("POST", "devices/id/reports", {})['authorization'] == 'Bearer token-2'
    assert len(tokens) == 2


def test_http_transient_exhaustion_stops_after_three_attempts(monkeypatch, clock):
    calls = []
    def send(request, timeout):
        calls.append(request)
        raise HTTPError(request.full_url, 503, "private", {}, None)
    monkeypatch.setattr(sim, "urlopen", send)
    with pytest.raises(RuntimeError, match="HTTP 503"):
        sim.Client("http://localhost", "http://localhost/realm", "team", "secret", 0.25).request("POST", "devices/id/reports", {})
    assert len(calls) == 3


def test_redirect_never_forwards_authorization_or_secret(clock):
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
    from threading import Thread
    requests = []
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            requests.append(self.path)
            self.send_response(307)
            self.send_header('Location', '/destination')
            self.end_headers()
        def log_message(self, *args):
            pass
    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        origin = f'http://127.0.0.1:{server.server_port}'
        client = sim.Client(origin, origin, 'team', 'private-secret', 0.25)
        with pytest.raises(RuntimeError, match='HTTP 307'):
            client.request('POST', 'devices/id/reports', {})
        client = sim.Client(origin, origin, 'team', None, 0.25, admin_token='private-token')
        with pytest.raises(RuntimeError, match='HTTP 307'):
            client.request('POST', 'devices', {})
        assert requests == ['/protocol/openid-connect/token', '/api/v1/organizations/team/devices']
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
