import { Injectable, inject } from '@angular/core';
import { formatDate } from '@angular/common';
import { NgbDateStruct, NgbDatepickerI18n } from '@ng-bootstrap/ng-bootstrap';
import { LanguageService } from 'src/app/core/services/language.service';

@Injectable()
export class AppDatepickerI18n extends NgbDatepickerI18n {
  private readonly languageService = inject(LanguageService);

  private get locale(): string {
    return this.languageService.currentLang();
  }

  getWeekdayLabel(weekday: number, width: Exclude<Intl.DateTimeFormatOptions['weekday'], undefined> = 'short'): string {
    return new Intl.DateTimeFormat(this.locale, { weekday: width, timeZone: 'UTC' }).format(Date.UTC(2000, 4, weekday));
  }

  getMonthShortName(month: number): string {
    return new Intl.DateTimeFormat(this.locale, { month: 'short', timeZone: 'UTC' }).format(Date.UTC(2000, month - 1));
  }

  getMonthFullName(month: number): string {
    return new Intl.DateTimeFormat(this.locale, { month: 'long', timeZone: 'UTC' }).format(Date.UTC(2000, month - 1));
  }

  getDayAriaLabel(date: NgbDateStruct): string {
    return formatDate(new Date(date.year, date.month - 1, date.day), 'fullDate', this.locale);
  }
}
