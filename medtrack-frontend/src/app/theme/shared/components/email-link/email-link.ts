import { Component, input } from '@angular/core';

@Component({
  selector: 'app-email-link',
  standalone: true,
  templateUrl: './email-link.html'
})
export class EmailLinkComponent {
  value = input.required<string>();
}
