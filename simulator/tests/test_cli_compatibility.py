"""Freeze existing CLI/state behavior before A1/A2 client changes."""
import json
import sys

import pytest
import fleetops_sim as sim


@pytest.fixture
def cli(monkeypatch, tmp_path):
    calls = []
    instances = []

    class Client:
        def __init__(self, api, issuer, org, secret, delay, **kwargs):
            instances.append((api, issuer, org, delay))

        def request(self, method, path, body):
            calls.append((method, path, body))
            return {**body, "id": body.get("id", "device-" + str(len(calls))),
                    "name": body.get("name", "MacBook"), "compliance": "UNKNOWN"}

    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("FLEETOPS_CLIENT_SECRET", "machine-fixture")
    monkeypatch.setenv("FLEETOPS_ADMIN_TOKEN", "short-lived-admin-fixture")
    monkeypatch.setattr(sim, "Client", Client)
    return calls, instances


def test_default_register_arguments_and_state(cli, monkeypatch, tmp_path):
    calls, instances = cli
    monkeypatch.setattr(sys, "argv", ["fleetops-sim", "register"])
    sim.main()
    assert instances == [("http://localhost:8080", "http://localhost:8180/realms/fleetops", "demo", 0.25)]
    assert len(calls) == 20
    assert calls[0] == ("POST", "devices", next(sim.fleet(20, 42)))
    state = json.loads((tmp_path / ".fleetops-state.json").read_text())
    assert state["api"] == "http://localhost:8080"
    assert state["org"] == "demo"
    assert len(state["devices"]) == 20
    sim.main()
    assert len(calls) == 20  # Existing serials are not provisioned again.


def test_explicit_arguments_keep_their_meaning(cli, monkeypatch, tmp_path):
    calls, instances = cli
    monkeypatch.setattr(sys, "argv", ["fleetops-sim", "register", "--count", "2", "--seed", "7",
        "--api", "https://api.example.test", "--issuer", "https://identity.example.test/realm",
        "--org", "team", "--state", str(tmp_path / "saved.json"), "--delay", "0.5",
        "--scenario", "outdated"])
    sim.main()
    assert instances == [("https://api.example.test", "https://identity.example.test/realm", "team", 0.5)]
    assert [c[2] for c in calls] == list(sim.fleet(2, 7))
    assert (tmp_path / "saved.json").exists()


def test_old_state_and_pending_report_are_reused_exactly(cli, monkeypatch, tmp_path):
    calls, _ = cli
    pending = {"reportId": "unchanged", "sequence": 4, "reportedAt": "2026-01-01T00:00:00Z",
               "osVersion": "15.0", "encryption": False, "screenLock": True, "firewall": True}
    state = {"api": "http://localhost:8080", "org": "demo", "formatHint": "preserve",
             "devices": [{"id": "old-device", "name": "MacBook", "sequence": 3, "pending": pending}]}
    path = tmp_path / ".fleetops-state.json"
    path.write_text(json.dumps(state))
    monkeypatch.setattr(sys, "argv", ["fleetops-sim", "check-in", "--count", "1"])
    sim.main()
    assert calls == [("POST", "devices/old-device/reports", pending)]
    saved = json.loads(path.read_text())
    assert saved["formatHint"] == "preserve"
    assert saved["devices"][0]["sequence"] == 4
    assert "pending" not in saved["devices"][0]


def test_state_cannot_be_reused_for_another_tenant(cli, monkeypatch, tmp_path):
    calls, _ = cli
    path = tmp_path / ".fleetops-state.json"
    original = json.dumps({"api": "http://localhost:8080", "org": "other", "devices": []})
    path.write_text(original)
    monkeypatch.setattr(sys, "argv", ["fleetops-sim", "check-in"])
    with pytest.raises(SystemExit) as error:
        sim.main()
    assert error.value.code == 2
    assert not calls
    assert path.read_text() == original


@pytest.mark.parametrize("arguments", [["--count", "0"], ["--count", "1001"], ["--delay", "0.1"],
                                        ["--api", "http://remote.example.test"]])
def test_safety_limits_remain_enforced(cli, monkeypatch, arguments):
    calls, _ = cli
    monkeypatch.setattr(sys, "argv", ["fleetops-sim", "register", *arguments])
    with pytest.raises(SystemExit) as error:
        sim.main()
    assert error.value.code == 2
    assert not calls


def test_register_requires_explicit_admin_token(cli, monkeypatch):
    monkeypatch.delenv('FLEETOPS_ADMIN_TOKEN')
    monkeypatch.setattr(sys, 'argv', ['fleetops-sim', 'register'])
    with pytest.raises(SystemExit) as error:
        sim.main()
    assert error.value.code == 2
    assert not cli[0]


def test_failed_report_preserves_pending_state_and_never_uses_admin_token(cli, monkeypatch, tmp_path):
    pending = {"reportId": "retry-me", "sequence": 2, "reportedAt": "2026-01-01T00:00:00Z"}
    original = {"api": "http://localhost:8080", "org": "demo", "devices": [
        {"id": "id", "name": "Mac", "sequence": 1, "pending": pending}]}
    path = tmp_path / '.fleetops-state.json'
    path.write_text(json.dumps(original))
    class FailingClient:
        def __init__(self, api, issuer, org, secret, delay, admin_token):
            assert secret == 'machine-fixture'
            assert admin_token is None
        def request(self, method, path, body):
            assert body == pending
            raise RuntimeError('API returned HTTP 403; state preserved')
    monkeypatch.setattr(sim, 'Client', FailingClient)
    monkeypatch.setattr(sys, 'argv', ['fleetops-sim', 'check-in'])
    with pytest.raises(SystemExit) as error:
        sim.main()
    assert error.value.code == 1
    assert json.loads(path.read_text()) == original


@pytest.mark.parametrize('delay', ['nan', 'inf', '-inf'])
def test_non_finite_delay_is_rejected(cli, monkeypatch, delay):
    monkeypatch.setattr(sys, 'argv', ['fleetops-sim', 'register', '--delay=' + delay])
    with pytest.raises(SystemExit) as error:
        sim.main()
    assert error.value.code == 2
    assert not cli[0]
