import { Component, inject, signal, effect } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Api, Policy, Page, errorMessage } from './api';

export const RULES = [
  { type: 'ENCRYPTION', label: 'Disk encryption', initial: 'true' },
  { type: 'SCREEN_LOCK', label: 'Screen lock', initial: 'true' },
  { type: 'FIREWALL', label: 'Firewall', initial: 'true' },
  { type: 'MIN_OS', label: 'Minimum OS version', initial: '15.0' },
  { type: 'MAX_AGE_HOURS', label: 'Maximum check-in age', initial: '24' },
  { type: 'ALLOWED_TYPE', label: 'Allowed device types', initial: 'MAC,IPHONE,IPAD' },
];
function validRule(control: AbstractControl) {
  const { type, value } = control.value;
  const pattern =
    type === 'MIN_OS'
      ? /^[0-9]{1,5}(\.[0-9]{1,5}){0,3}$/
      : type === 'MAX_AGE_HOURS'
        ? /^[1-9][0-9]{0,4}$/
        : type === 'ALLOWED_TYPE'
          ? /^(MAC|IPHONE|IPAD)(,(MAC|IPHONE|IPAD)){0,2}$/
          : /^(true|false)$/;
  return RULES.some((r) => r.type === type) && pattern.test(value) ? null : { ruleValue: true };
}
@Component({
  selector: 'app-policies',
  imports: [ReactiveFormsModule, MatButtonModule, DatePipe],
  templateUrl: './policies.html',
})
export class Policies {
  readonly api = inject(Api);
  private readonly fb = inject(FormBuilder);
  readonly ruleOptions = RULES;
  readonly deviceTypes = [
    { value: 'MAC', label: 'Mac' },
    { value: 'IPHONE', label: 'iPhone' },
    { value: 'IPAD', label: 'iPad' },
  ];
  readonly policies = signal<Policy[]>([]);
  readonly error = signal('');
  readonly notice = signal('');
  readonly busy = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<Policy | null>(null);
  readonly open = signal(false);
  readonly next = signal<string | null>(null);
  readonly historyPolicy = signal<Policy | null>(null);
  readonly versions = signal<Policy[]>([]);
  readonly historyNext = signal<string | null>(null);
  readonly historyBusy = signal(false);
  readonly historyError = signal('');
  private historyRequest = 0;
  readonly rules = this.fb.array([this.ruleGroup('ENCRYPTION', 'true')], {
    validators: [
      Validators.minLength(1),
      (c) => {
        const types = c.value.map((r: { type: string }) => r.type);
        return new Set(types).size === types.length ? null : { duplicate: true };
      },
    ],
  });
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
    description: ['', Validators.maxLength(500)],
    targets: this.fb.nonNullable.group(
      { MAC: true, IPHONE: false, IPAD: false },
      {
        validators: (c) => (Object.values(c.value).some(Boolean) ? null : { targetRequired: true }),
      },
    ),
    severity: ['HIGH'],
    enabled: [true],
    rules: this.rules,
  });
  constructor() {
    effect(() => {
      this.api.organization();
      this.open.set(false);
      this.closeHistory();
      void this.load();
    });
  }
  private ruleGroup(type: string, value: string) {
    return this.fb.nonNullable.group({ type, value }, { validators: validRule });
  }
  ruleLabel(type: string): string {
    return RULES.find((r) => r.type === type)?.label ?? type;
  }
  targetsLabel(types: string[]): string {
    return types.map((t) => this.deviceTypes.find((d) => d.value === t)?.label ?? t).join(', ');
  }
  valueLabel(type: string, value: string): string {
    if (type === 'MAX_AGE_HOURS') return value + ' hours';
    if (type === 'ALLOWED_TYPE') return this.targetsLabel(value.split(','));
    return value === 'true' ? 'Enabled' : value === 'false' ? 'Disabled' : value;
  }
  changeRule(index: number): void {
    const row = this.rules.at(index);
    row.controls.value.setValue(
      RULES.find((r) => r.type === row.controls.type.value)?.initial ?? '',
    );
  }
  addRule(): void {
    const option = RULES.find((r) => !this.rules.value.some((v) => v.type === r.type));
    if (option) this.rules.push(this.ruleGroup(option.type, option.initial));
  }
  removeRule(index: number): void {
    if (this.rules.length > 1) this.rules.removeAt(index);
  }
  async load(cursor?: string): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const p = await this.api.get<Page<Policy>>(
        'policies?limit=25' + (cursor ? '&cursor=' + encodeURIComponent(cursor) : ''),
      );
      this.policies.set(p.items);
      this.next.set(p.nextCursor);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  edit(p: Policy | null): void {
    this.editing.set(p);
    this.notice.set('');
    this.rules.clear();
    for (const r of p?.rules ?? [{ type: 'ENCRYPTION', value: 'true' }])
      this.rules.push(this.ruleGroup(r.type, r.value));
    this.form.patchValue({
      name: p?.name ?? '',
      description: p?.description ?? '',
      severity: p?.severity ?? 'HIGH',
      enabled: p?.enabled ?? true,
      targets: {
        MAC: p ? p.targetTypes.includes('MAC') : true,
        IPHONE: p?.targetTypes.includes('IPHONE') ?? false,
        IPAD: p?.targetTypes.includes('IPAD') ?? false,
      },
    });
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.open.set(true);
  }
  async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving() || !this.api.admin) return;
    this.saving.set(true);
    this.error.set('');
    try {
      const v = this.form.getRawValue();
      const previous = this.editing();
      const input = {
        name: v.name.trim(),
        description: v.description,
        targetTypes: Object.entries(v.targets)
          .filter(([, selected]) => selected)
          .map(([type]) => type),
        severity: v.severity,
        enabled: v.enabled,
        rules: v.rules,
        version: previous?.version ?? 0,
      };
      if (previous) await this.api.put('policies/' + previous.id, input);
      else await this.api.post('policies', input);
      this.open.set(false);
      this.closeHistory();
      this.notice.set('Policy saved. Evaluate devices to apply the updated requirements.');
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.saving.set(false);
    }
  }
  closeHistory(): void {
    this.historyRequest++;
    this.historyPolicy.set(null);
    this.versions.set([]);
    this.historyNext.set(null);
    this.historyBusy.set(false);
  }
  async showHistory(policy: Policy, cursor?: string): Promise<void> {
    const request = ++this.historyRequest;
    this.historyPolicy.set(policy);
    if (!cursor) this.versions.set([]);
    this.historyBusy.set(true);
    this.historyError.set('');
    try {
      const page = await this.api.get<Page<Policy>>(
        'policies/' +
          encodeURIComponent(policy.id) +
          '/history?limit=10' +
          (cursor ? '&cursor=' + encodeURIComponent(cursor) : ''),
      );
      if (request !== this.historyRequest) return;
      this.versions.update((items) => (cursor ? [...items, ...page.items] : page.items));
      this.historyNext.set(page.nextCursor);
    } catch (e) {
      if (request === this.historyRequest) this.historyError.set(errorMessage(e));
    } finally {
      if (request === this.historyRequest) this.historyBusy.set(false);
    }
  }
}
