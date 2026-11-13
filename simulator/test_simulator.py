import unittest
from datetime import datetime, timezone
from fleetops_sim import fleet, telemetry

class SimulatorTests(unittest.TestCase):
    def test_reproducible_fleet(self):
        self.assertEqual(list(fleet(100, 42)), list(fleet(100, 42)))
        self.assertNotEqual(list(fleet(5, 42)), list(fleet(5, 43)))
    def test_unique_serials(self):
        self.assertEqual(100, len({d['serial'] for d in fleet(100, 42)}))
    def test_scenarios(self):
        device = next(fleet(1, 42))
        self.assertFalse(telemetry(device, 1, 'noncompliant')['encryption'])
        self.assertEqual('12.0', telemetry(device, 2, 'outdated')['osVersion'])
        reported_at = datetime.fromisoformat(telemetry(device, 3, 'inactive')['reportedAt'])
        age = datetime.now(timezone.utc) - reported_at
        self.assertGreaterEqual(age.days, 7)
    def test_sequence(self):
        self.assertEqual(42, telemetry(next(fleet(1, 1)), 42, 'healthy')['sequence'])

if __name__ == '__main__':
    unittest.main()
