import { Pipe, PipeTransform } from '@angular/core';
import { formatTimeDisplay } from '../utils/date-format';

@Pipe({ name: 'timeFormat', standalone: true })
export class TimeFormatPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatTimeDisplay(value);
  }
}
