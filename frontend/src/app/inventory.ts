import { lifecycleLabel } from './lifecycle';
import { Component, inject, signal, effect } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Api, Device, Page, errorMessage } from './api';
@Component({
  selector: 'app-inventory',
  imports: [DatePipe, RouterLink, ReactiveFormsModule, MatButtonModule],
  templateUrl: './inventory.html',
})
export class Inventory {
  readonly api = inject(Api);
  readonly lifecycleLabel = lifecycleLabel;
  private readonly fb = inject(FormBuilder);
  readonly devices = signal<Device[]>([]);
  readonly next = signal<string | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly formOpen = signal(false);
  readonly notice = signal('');
  private generation = 0;
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    type: ['MAC', Validators.required],
    model: ['', Validators.required],
    osVersion: ['', [Validators.pattern(/^[0-9]{1,5}(\.[0-9]{1,5}){0,3}$/)]],
    serial: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{3,64}$/)]],
    employee: [''],
  });
  constructor() {
    effect(() => {
      this.api.organization();
      void this.load();
    });
  }
  async load(cursor?: string): Promise<void> {
    const run = ++this.generation;
    this.loading.set(true);
    this.error.set('');
    try {
      const page = await this.api.get<Page<Device>>(
        'devices?limit=25' + (cursor ? '&cursor=' + encodeURIComponent(cursor) : ''),
      );
      if (run === this.generation) {
        this.devices.set(page.items);
        this.next.set(page.nextCursor);
      }
    } catch (e) {
      if (run === this.generation) this.error.set(errorMessage(e));
    } finally {
      if (run === this.generation) this.loading.set(false);
    }
  }
  async register(): Promise<void> {
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set('');
    try {
      const v = this.form.getRawValue();
      await this.api.post<Device>('devices', {
        ...v,
        os: v.type === 'MAC' ? 'macOS' : v.type === 'IPHONE' ? 'iOS' : 'iPadOS',
        osVersion: v.osVersion || null,
        employee: v.employee || null,
      });
      this.formOpen.set(false);
      this.form.reset({ type: 'MAC' });
      this.notice.set('Device registered. Compliance is unknown until evaluated.');
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.saving.set(false);
    }
  }
}
