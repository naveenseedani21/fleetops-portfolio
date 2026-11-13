import { lifecycleLabel, lifecycleNeedsReview } from './lifecycle';
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
  readonly lifecycleLabel = lifecycleLabel;
  readonly lifecycleNeedsReview = lifecycleNeedsReview;
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  readonly device = signal<Device | null>(null);
  readonly history = signal<Evidence[]>([]);
  readonly historyNext = signal<string | null>(null);
  readonly historyBusy = signal(false);
  readonly historyError = signal('');
  private generation = 0;
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
    const generation = ++this.generation;
    this.historyNext.set(null);
    this.historyBusy.set(false);
    this.historyError.set('');
    this.device.set(null);
    this.history.set([]);
    this.error.set('');
    this.busy.set(true);
    try {
      const id = this.route.snapshot.paramMap.get('id')!;
      const [d, h] = await Promise.all([
        this.api.get<Device>('devices/' + id),
        this.api.get<Page<Evidence>>('devices/' + id + '/evaluations?limit=25'),
      ]);
      if (generation !== this.generation) return;
      this.device.set(d);
      this.history.set(h.items);
      this.historyNext.set(h.nextCursor);
      this.form.setValue({ name: d.name, employee: d.employee ?? '' });
    } catch (e) {
      if (generation === this.generation) this.error.set(errorMessage(e));
    } finally {
      if (generation === this.generation) this.busy.set(false);
    }
  }
  async loadOlder(): Promise<void> {
    const cursor = this.historyNext();
    const device = this.device();
    if (!cursor || !device || this.historyBusy() || this.busy()) return;
    const generation = this.generation;
    this.historyBusy.set(true);
    this.historyError.set('');
    try {
      const page = await this.api.get<Page<Evidence>>(
        'devices/' +
          encodeURIComponent(device.id) +
          '/evaluations?limit=25&cursor=' +
          encodeURIComponent(cursor),
      );
      if (generation !== this.generation) return;
      this.history.update((items) => [
        ...items,
        ...page.items.filter((item) => !items.some((existing) => existing.id === item.id)),
      ]);
      this.historyNext.set(page.nextCursor);
    } catch (e) {
      if (generation === this.generation) this.historyError.set(errorMessage(e));
    } finally {
      if (generation === this.generation) this.historyBusy.set(false);
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
