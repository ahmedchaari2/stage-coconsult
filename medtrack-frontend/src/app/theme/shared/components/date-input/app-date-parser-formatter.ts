import { Injectable } from '@angular/core';
import { NgbDateParserFormatter, NgbDateStruct } from '@ng-bootstrap/ng-bootstrap';

const DATE_REGEX = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/;

@Injectable()
export class AppDateParserFormatter extends NgbDateParserFormatter {
  parse(value: string): NgbDateStruct | null {
    if (!value) return null;
    const match = DATE_REGEX.exec(value.trim());
    if (!match) return null;
    return { day: Number(match[1]), month: Number(match[2]), year: Number(match[3]) };
  }

  format(date: NgbDateStruct | null): string {
    if (!date) return '';
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(date.day)}/${pad(date.month)}/${date.year}`;
  }
}
