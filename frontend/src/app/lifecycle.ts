import { Device } from './api';
const states = new Set(['REGISTERED', 'ACTIVE', 'INACTIVE', 'RETIRED']);
export function lifecycleNeedsReview(device: Pick<Device, 'status'>): boolean {
  return !states.has(device.status);
}
export function lifecycleLabel(device: Pick<Device, 'status'>): string {
  return lifecycleNeedsReview(device) ? 'Needs review' : device.status;
}
