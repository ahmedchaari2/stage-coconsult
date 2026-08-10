import { Pipe, PipeTransform } from '@angular/core';
import { formatDateTimeDisplay } from '../utils/date-format';

@Pipe({ name: 'dateTimeFormat', standalone: true })
export class DateTimeFormatPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatDateTimeDisplay(value);
  }
}
