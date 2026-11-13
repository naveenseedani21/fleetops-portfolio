import { Component, input } from '@angular/core';

@Component({
  selector: 'app-icon',
  template: `<svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path [attr.d]="paths[name()] || paths['device']" />
  </svg>`,
  styles: ':host { display: inline-flex; flex: 0 0 auto; } svg { width: 20px; height: 20px; }',
})
export class Icon {
  readonly name = input('device');
  readonly paths: Record<string, string> = {
    overview: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
    device: 'M5 4h14v12H5z M2 20h20l-3-4H5z',
    shield: 'M12 3l8 3v6c0 5-8 9-8 9s-8-4-8-9V6z M8 12l3 3 5-6',
    history: 'M3 11a9 9 0 1 1 2 7 M3 4v7h7 M12 7v5l3 2',
  };
}
