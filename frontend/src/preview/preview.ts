import { Component, computed, signal } from '@angular/core';
import { Icon } from '../app/icon';

const stages = [
  {
    title: 'Provisioned',
    lifecycle: 'REGISTERED',
    compliance: 'UNKNOWN',
    os: '16.0',
    encryption: 'Unknown',
    detail: 'An administrator registers the device. No telemetry has been accepted yet.',
  },
  {
    title: 'Policy failure',
    lifecycle: 'ACTIVE',
    compliance: 'NONCOMPLIANT',
    os: '16.0',
    encryption: 'Disabled',
    detail:
      'The machine report fails encryption and minimum OS requirements. Lifecycle is ACTIVE; compliance is NONCOMPLIANT.',
  },
  {
    title: 'Recovered',
    lifecycle: 'ACTIVE',
    compliance: 'COMPLIANT',
    os: '17.0',
    encryption: 'Enabled',
    detail:
      'A corrected report restores compliance. The earlier failure remains in the evidence history.',
  },
  {
    title: 'Retired',
    lifecycle: 'RETIRED',
    compliance: 'COMPLIANT',
    os: '17.0',
    encryption: 'Enabled',
    detail:
      'Retirement is independent of compliance. A passing evaluation cannot reactivate a retired device.',
  },
] as const;

@Component({
  selector: 'app-root',
  imports: [Icon],
  templateUrl: './preview.html',
  styleUrl: './preview.scss',
})
export class Preview {
  readonly view = signal('inventory');
  readonly stage = signal(1);
  readonly current = computed(() => stages[this.stage()]);
  readonly stages = stages;
  readonly selected = signal(0);
  readonly devices = ['Design MacBook', 'Engineering MacBook', 'Studio iPad', 'Field iPhone'];
  readonly role = signal('Administrator');
  readonly notice = signal('');
  readonly source = 'https://github.com/naveenseedani21/fleetops-portfolio';
  readonly evidence = [
    {
      file: 'operations-inventory.jpg',
      title: 'Inventory and device inspector',
      caption: 'Real local application · Operations design · 13 November 2026',
    },
    {
      file: 'reviewer-docker-failure.png',
      title: 'A report fails the policy',
      caption: 'Real authenticated Docker acceptance · earlier interface · 13 November 2026',
    },
    {
      file: 'reviewer-docker-recovery.png',
      title: 'A corrected report restores compliance',
      caption: 'Same real Docker workflow · retained failure evidence · earlier interface',
    },
    {
      file: 'operations-docker-viewer.jpg',
      title: 'Viewer workspace',
      caption: 'Real Keycloak viewer session · Operations design · no provisioning control',
    },
  ];
  chooseStage(index: number): void {
    this.stage.set(index);
    this.selected.set(0);
    this.notice.set('Preview state changed locally. No report was submitted to a server.');
  }
  reset(): void {
    this.stage.set(1);
    this.selected.set(0);
    this.role.set('Administrator');
    this.notice.set('Preview reset. All data is synthetic and stays in this page.');
  }
}
