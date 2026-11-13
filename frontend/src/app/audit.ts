import { Component, inject, signal, effect } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { Api, Audit, Page, errorMessage } from './api';
@Component({
  selector: 'app-audit',
  imports: [DatePipe, MatButtonModule],
  template: `<p class="eyebrow">WORKSPACE / ACTIVITY</p>
    <h1>Audit activity</h1>
    <p class="muted">
      Persisted operations, newest first. Audit events cannot be edited through the application.
    </p>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    <section class="panel">
      <div class="section-heading">
        <h2>Operation history</h2>
        <button mat-button (click)="load()" [disabled]="busy()">Refresh / first page</button>
      </div>
      @if (busy()) {
        <p role="status">Loading…</p>
      }
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Action</th>
              <th>Actor</th>
              <th>Target</th>
              <th>Outcome</th>
            </tr>
          </thead>
          <tbody>
            @for (e of events(); track e.id) {
              <tr>
                <td>{{ e.timestamp | date: 'medium' }}</td>
                <td>{{ e.action.replaceAll('_', ' ') }}</td>
                <td class="mono">{{ e.actorId }}</td>
                <td class="mono">{{ e.targetId }}</td>
                <td>{{ e.outcome }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5">No operations recorded.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span>Up to 25 events per page</span
        ><button mat-stroked-button [disabled]="!next() || busy()" (click)="load(next()!)">
          Next page →
        </button>
      </div>
    </section>`,
})
export class AuditView {
  readonly api = inject(Api);
  readonly events = signal<Audit[]>([]);
  readonly next = signal<string | null>(null);
  readonly error = signal('');
  readonly busy = signal(false);
  constructor() {
    effect(() => {
      this.api.organization();
      void this.load();
    });
  }
  async load(cursor?: string): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const p = await this.api.get<Page<Audit>>(
        'audit-events?limit=25' + (cursor ? '&cursor=' + encodeURIComponent(cursor) : ''),
      );
      this.events.set(p.items);
      this.next.set(p.nextCursor);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
