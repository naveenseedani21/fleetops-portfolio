import { Component, inject, signal, effect } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Api, Device, Evidence, Page, errorMessage } from './api';
@Component({
  selector: 'app-detail',
  imports: [RouterLink, DatePipe, ReactiveFormsModule, MatButtonModule],
  templateUrl: './detail.html',
})
export class Detail {
  readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  readonly device = signal<Device | null>(null);
  readonly history = signal<Evidence[]>([]);
  readonly error = signal('');
  readonly busy = signal(false);
  readonly confirming = signal(false);
  readonly notice = signal('');
  readonly form = this.fb.nonNullable.group({ name: ['', Validators.required], employee: [''] });
  constructor() {
    effect(() => {
      this.api.organization();
      void this.load();
    });
  }
  async load(): Promise<void> {
    this.device.set(null);
    this.history.set([]);
    this.error.set('');
    this.busy.set(true);
    try {
      const id = this.route.snapshot.paramMap.get('id')!;
      const [d, h] = await Promise.all([
        this.api.get<Device>('devices/' + id),
        this.api.get<Page<Evidence>>('devices/' + id + '/evaluations'),
      ]);
      this.device.set(d);
      this.history.set(h.items);
      this.form.setValue({ name: d.name, employee: d.employee ?? '' });
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  async act(action: 'save' | 'retire' | 'evaluate'): Promise<void> {
    const d = this.device();
    if (!d || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      if (action === 'save')
        await this.api.patch('devices/' + d.id, {
          revision: d.revision,
          ...this.form.getRawValue(),
        });
      else
        await this.api.post(
          'devices/' + d.id + (action === 'retire' ? '/retire' : '/evaluations'),
          { revision: d.revision },
        );
      this.confirming.set(false);
      this.notice.set('Change saved.');
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
