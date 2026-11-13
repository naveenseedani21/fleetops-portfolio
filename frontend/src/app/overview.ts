import { Component, effect, inject, signal } from '@angular/core';
import { DatePipe, KeyValuePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { Api, Audit, Page, errorMessage } from './api';
interface Summary {
  total: number;
  lifecycleReviewRequired?: number;
  retired: number;
  active: number;
  stale: number;
  noncompliant: number;
  unknown: number;
  evaluated: number;
  compliancePercent: number | null;
  operatingSystems: Record<string, number>;
  compliance: Record<string, number>;
  calculatedAt: string;
}
@Component({
  selector: 'app-overview',
  imports: [DatePipe, KeyValuePipe, RouterLink, MatButtonModule],
  template: `
    <p class="eyebrow">WORKSPACE / OVERVIEW</p>
    <div class="page-heading">
      <div>
        <h1>Your fleet at a glance</h1>
        <p class="muted">Live inventory and last-evaluated security posture.</p>
      </div>
      <button mat-stroked-button (click)="load()" [disabled]="loading()">Refresh overview</button>
    </div>
    @if (error()) {
      <p role="alert" class="error">{{ error() }}</p>
    }
    @if (loading()) {
      <p role="status">Calculating fleet summary…</p>
    }
    @if (summary(); as s) {
      @if (s.lifecycleReviewRequired) {
        <p class="notice">
          {{ s.lifecycleReviewRequired }}
          {{ s.lifecycleReviewRequired === 1 ? 'device needs' : 'devices need' }} lifecycle review.
          <a routerLink="/devices">Review inventory</a>
        </p>
      }
      <div class="metric-grid">
        <section class="panel metric">
          <span>Total registered</span><strong>{{ s.total }}</strong
          ><small>Includes {{ s.retired }} retired</small>
        </section>
        <section class="panel metric">
          <span>Active devices</span><strong>{{ s.active }}</strong
          ><small>Devices with active lifecycle</small>
        </section>
        <section class="panel metric">
          <span>Noncompliant</span><strong>{{ s.noncompliant }}</strong
          ><small>Last evaluation failed</small>
        </section>
        <section class="panel metric">
          <span>Stale check-ins</span><strong>{{ s.stale }}</strong
          ><small>Over 24 hours or never reported</small>
        </section>
      </div>
      <div class="detail-grid">
        <section class="panel">
          <h2>Compliance distribution</h2>
          <p class="compliance-score">
            {{ s.compliancePercent === null ? '—' : s.compliancePercent + '%' }}
          </p>
          <p class="muted">
            Passing among {{ s.evaluated }} devices with a pass/fail result. Unknown and
            not-applicable outcomes are excluded.
          </p>
          @for (c of s.compliance | keyvalue; track c.key) {
            <div class="bar-label">
              <span>{{ c.key.replaceAll('_', ' ') }}</span><strong>{{ c.value }}</strong>
            </div>
            <meter
              min="0"
              [max]="s.total - s.retired || 1"
              [value]="c.value"
              [attr.aria-label]="c.key + ' devices'"
            >
              {{ c.value }}
            </meter>
          } @empty {
            <p>No current devices.</p>
          }
        </section>
        <section class="panel">
          <h2>Operating systems</h2>
          @for (os of s.operatingSystems | keyvalue; track os.key) {
            <div class="os-count">
              <span>{{ os.key }}</span><strong>{{ os.value }}</strong>
            </div>
          } @empty {
            <p>Register a device to see the fleet mix.</p>
          }
          <p class="muted">Retired devices are excluded from distribution and freshness metrics.</p>
          <a routerLink="/devices">View inventory →</a>
        </section>
      </div>
      <section class="panel">
        <div class="section-heading">
          <h2>Recent activity</h2>
          <a routerLink="/audit">View audit history →</a>
        </div>
        @for (event of events(); track event.id) {
          <div class="activity-row">
            <span>{{ event.action.replaceAll('_', ' ') }}</span><time>{{
              event.timestamp | date: 'medium'
            }}</time>
          </div>
        } @empty {
          <p>No activity yet.</p>
        }
      </section>
      <p class="muted">
        Updated {{ s.calculatedAt | date: 'medium' }}. Compliance reflects the latest device
        evaluations.
      </p>
    }
  `,
})
export class Overview {
  readonly api = inject(Api);
  readonly summary = signal<Summary | null>(null);
  readonly events = signal<Audit[]>([]);
  readonly error = signal('');
  readonly loading = signal(false);
  constructor() {
    effect(() => {
      this.api.organization();
      void this.load();
    });
  }
  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const [s, a] = await Promise.all([
        this.api.get<Summary>('dashboard/summary'),
        this.api.get<Page<Audit>>('audit-events?limit=8'),
      ]);
      this.summary.set(s);
      this.events.set(a.items);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
}
