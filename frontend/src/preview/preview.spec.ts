import { TestBed } from '@angular/core/testing';
import { Preview } from './preview';

describe('Standalone public preview', () => {
  it('boots without HTTP or identity providers and labels synthetic state', async () => {
    await TestBed.configureTestingModule({ imports: [Preview] }).compileComponents();
    const fixture = TestBed.createComponent(Preview);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Interactive preview · Synthetic data');
    expect(fixture.nativeElement.textContent).toContain('no login or backend connection');
    expect(fixture.nativeElement.querySelectorAll('form').length).toBe(0);
  });
  it('illustrates lifecycle independently and resets page state', () => {
    const preview = new Preview();
    preview.chooseStage(1);
    expect(preview.current().lifecycle).toBe('ACTIVE');
    expect(preview.current().compliance).toBe('NONCOMPLIANT');
    preview.chooseStage(2);
    expect(preview.current().lifecycle).toBe('ACTIVE');
    expect(preview.current().compliance).toBe('COMPLIANT');
    preview.chooseStage(3);
    expect(preview.current().lifecycle).toBe('RETIRED');
    preview.role.set('Viewer');
    preview.selected.set(2);
    preview.reset();
    expect(preview.role()).toBe('Administrator');
    expect(preview.selected()).toBe(0);
    expect(preview.stage()).toBe(1);
  });
});
