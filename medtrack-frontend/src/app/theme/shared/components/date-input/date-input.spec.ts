import { FormControl } from '@angular/forms';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';

import { DateInputComponent } from './date-input';

describe('DateInputComponent', () => {
  let fixture: ComponentFixture<DateInputComponent>;
  let component: DateInputComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        DateInputComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: { common: { datePicker: { open: 'Open calendar' } } } },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }
        })
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(DateInputComponent);
    component = fixture.componentInstance;
  });

  it('validates dates rejected by the supplied predicate', () => {
    fixture.componentRef.setInput('dateDisabled', (date: { year: number; month: number; day: number }) => date.day === 2);

    expect(component.validate(new FormControl('2026-08-02'))).toEqual({ dateDisabled: true });
    expect(component.validate(new FormControl('2026-08-03'))).toBeNull();
  });
});
