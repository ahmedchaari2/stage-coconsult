import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';

@Component({
  selector: 'app-list-error-state',
  standalone: true,
  imports: [TranslocoModule],
  templateUrl: './list-error-state.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ListErrorStateComponent {
  message = input.required<string>();
  retry = output<void>();
}
