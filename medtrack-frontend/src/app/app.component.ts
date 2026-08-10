import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { SpinnerComponent } from './theme/shared/components/spinner/spinner.component';
import { ToastContainerComponent } from './theme/shared/components/toast-container/toast-container.component';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
  imports: [RouterOutlet, SpinnerComponent, ToastContainerComponent]
})
export class AppComponent {
  title = 'medtrack-frontend';
}
