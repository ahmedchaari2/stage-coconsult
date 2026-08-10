import { Component, input, output, inject } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';

import { SharedModule } from 'src/app/theme/shared/shared.module';

import { IconService } from '@ant-design/icons-angular';
import { MenuUnfoldOutline, MenuFoldOutline, SearchOutline } from '@ant-design/icons-angular/icons';

@Component({
  selector: 'app-nav-left',
  imports: [SharedModule, TranslocoModule],
  templateUrl: './nav-left.component.html',
  styleUrls: ['./nav-left.component.scss']
})
export class NavLeftComponent {
  private iconService = inject(IconService);

  readonly navCollapsed = input.required<boolean>();
  readonly NavCollapse = output();
  readonly NavCollapsedMob = output();
  constructor() {
    this.iconService.addIcon(...[MenuUnfoldOutline, MenuFoldOutline, SearchOutline]);
  }

  navCollapse() {
    this.NavCollapse.emit();
  }

  navCollapsedMob() {
    this.NavCollapsedMob.emit();
  }
}
