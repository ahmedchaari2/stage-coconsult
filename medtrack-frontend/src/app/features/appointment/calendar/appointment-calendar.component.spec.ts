import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { Appointment } from 'src/app/core/models/appointment.model';
import { AppointmentService } from 'src/app/core/services/appointment.service';
import { AppointmentCalendarComponent } from './appointment-calendar.component';

const appointments: Appointment[] = Array.from({ length: 5 }, (_, index) => ({
  id: index + 1,
  patientId: index + 10,
  patientNom: `Patient ${index + 1}`,
  patientPrenom: 'Test',
  medecinId: 4,
  medecinNom: 'Doctor',
  medecinPrenom: 'Test',
  dateHeure: `2026-07-15T${String(8 + index).padStart(2, '0')}:00:00`,
  motif: 'Follow-up',
  statut: 'CONFIRME',
  notes: null
}));

describe('AppointmentCalendarComponent', () => {
  let fixture: ComponentFixture<AppointmentCalendarComponent>;
  let component: AppointmentCalendarComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AppointmentCalendarComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              appointment: {
                calendar: {
                  previous: 'Previous month',
                  next: 'Next month',
                  today: 'Today',
                  more: '+{{count}} more',
                  weekdays: { mon: 'Mon', tue: 'Tue', wed: 'Wed', thu: 'Thu', fri: 'Fri', sat: 'Sat', sun: 'Sun' }
                },
                statut: { CONFIRME: 'Confirmed' }
              },
              common: { loading: 'Loading', buttons: { retry: 'Retry' } }
            }
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }
        })
      ],
      providers: [{ provide: AppointmentService, useValue: { getCalendar: () => of(appointments) } }]
    }).compileComponents();

    fixture = TestBed.createComponent(AppointmentCalendarComponent);
    component = fixture.componentInstance;
    component.currentYear.set(2026);
    component.currentMonth.set(7);
    fixture.detectChanges();
  });

  it('limits busy days to three appointments and shows the remaining count', () => {
    const busyDay = Array.from(fixture.nativeElement.querySelectorAll('.calendar-day')).find((day) =>
      (day as HTMLElement).querySelector('.calendar-more-button')
    ) as HTMLElement;

    expect(busyDay.querySelectorAll('.calendar-appointment-chip')).toHaveLength(3);
    expect(busyDay.querySelector('.calendar-more-button')?.textContent).toContain('+2 more');
  });

  it('emits the date without triggering empty-day creation when more is selected', () => {
    const selectedDates: string[] = [];
    const createDates: string[] = [];
    component.dayAppointmentsClick.subscribe((date) => selectedDates.push(date));
    component.dayClick.subscribe((date) => createDates.push(date));

    (fixture.nativeElement.querySelector('.calendar-more-button') as HTMLButtonElement).click();

    expect(selectedDates).toEqual(['2026-07-15']);
    expect(createDates).toEqual([]);
  });

  it('does not start appointment creation from a Sunday calendar cell', () => {
    const createDates: string[] = [];
    component.dayClick.subscribe((date) => createDates.push(date));
    const sunday = component
      .weeks()
      .flat()
      .find((day) => day.inCurrentMonth && day.date.getDay() === 0)!;

    component.onDayClick(sunday);

    expect(createDates).toEqual([]);
  });
});
