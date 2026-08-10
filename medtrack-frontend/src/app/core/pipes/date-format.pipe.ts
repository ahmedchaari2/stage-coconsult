import { Pipe, PipeTransform } from '@angular/core';
import { formatDateDisplay } from '../utils/date-format';

/** Affichage de date unique pour toute l'app : jj/mm/aaaa, quelle que soit la locale du navigateur. */
@Pipe({ name: 'dateFormat', standalone: true })
export class DateFormatPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatDateDisplay(value);
  }
}
