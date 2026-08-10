import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';

import { ListErrorStateComponent } from './list-error-state.component';

describe('ListErrorStateComponent', () => {
  let fixture: ComponentFixture<ListErrorStateComponent>;
  let component: ListErrorStateComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ListErrorStateComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: { common: { buttons: { retry: 'Retry' } } } },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }
        })
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ListErrorStateComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('message', 'Unable to load items');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('renders the error message and retry action', () => {
    const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLDivElement;
    const retryButton = fixture.nativeElement.querySelector('button') as HTMLButtonElement;

    expect(alert.textContent).toContain('Unable to load items');
    expect(retryButton.textContent).toContain('Retry');
  });

  it('emits retry when the action is activated', () => {
    let retries = 0;
    component.retry.subscribe(() => retries++);

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();

    expect(retries).toBe(1);
  });
});
