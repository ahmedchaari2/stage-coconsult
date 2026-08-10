import { Component, input } from '@angular/core';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-auth-card-header',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './auth-card-header.component.html'
})
export class AuthCardHeaderComponent {
  title = input.required<string>();

  linkLabel = input<string | null>(null);
  linkRoute = input<string[] | null>(null);
}
