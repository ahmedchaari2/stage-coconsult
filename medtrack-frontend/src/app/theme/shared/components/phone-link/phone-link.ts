import { Component, input } from '@angular/core';
import { PhoneFormatPipe } from 'src/app/core/pipes/phone-format.pipe';

@Component({
  selector: 'app-phone-link',
  standalone: true,
  imports: [PhoneFormatPipe],
  templateUrl: './phone-link.html'
})
export class PhoneLinkComponent {
  value = input.required<string>();
}
