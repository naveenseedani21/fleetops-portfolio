import { Component, inject, signal, effect } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Api, Policy, Page, errorMessage } from './api';
@Component({
  selector: 'app-policies',
  imports: [ReactiveFormsModule, MatButtonModule],
  templateUrl: './policies.html',
})
export class Policies {
  readonly api = inject(Api);
  private readonly fb = inject(FormBuilder);
  readonly policies = signal<Policy[]>([]);
  readonly error = signal('');
  readonly busy = signal(false);
  readonly editing = signal<Policy | null>(null);
  readonly open = signal(false);
  readonly next = signal<string | null>(null);
  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    description: [''],
    type: ['MAC'],
    severity: ['HIGH'],
    rule: ['ENCRYPTION'],
    value: ['true', Validators.required],
    enabled: [true],
  });
  constructor() {
    effect(() => {
      this.api.organization();
      this.open.set(false);
      void this.load();
    });
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
    this.form.setValue({
      name: p?.name ?? '',
      description: p?.description ?? '',
      type: p?.targetTypes[0] ?? 'MAC',
      severity: p?.severity ?? 'HIGH',
      rule: p?.rules[0]?.type ?? 'ENCRYPTION',
      value: p?.rules[0]?.value ?? 'true',
      enabled: p?.enabled ?? true,
    });
    this.open.set(true);
  }
  async save(): Promise<void> {
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const v = this.form.getRawValue();
      const previous = this.editing();
      const input = {
        name: v.name,
        description: v.description,
        targetTypes: [v.type],
        severity: v.severity,
        enabled: v.enabled,
        rules: [{ type: v.rule, value: v.value }],
        version: previous?.version ?? 0,
      };
      if (previous) await this.api.put('policies/' + previous.id, input);
      else await this.api.post('policies', input);
      this.open.set(false);
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
