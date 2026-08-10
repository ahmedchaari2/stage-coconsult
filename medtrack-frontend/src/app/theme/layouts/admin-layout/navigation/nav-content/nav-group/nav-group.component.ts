import { Component, OnInit, input, output, inject, computed } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { TranslocoModule } from '@jsverse/transloco';

import { NavigationItem } from '../../navigation';
import { SharedModule } from 'src/app/theme/shared/shared.module';
import { NavCollapseComponent } from '../nav-collapse/nav-collapse.component';
import { NavItemComponent } from '../nav-item/nav-item.component';
import { AuthService } from 'src/app/core/services/auth.service';

@Component({
  selector: 'app-nav-group',
  imports: [CommonModule, SharedModule, NavCollapseComponent, NavItemComponent, TranslocoModule],
  templateUrl: './nav-group.component.html',
  styleUrls: ['./nav-group.component.scss']
})
export class NavGroupComponent implements OnInit {
  private location = inject(Location);
  private authService = inject(AuthService);

  readonly item = input.required<NavigationItem>();
  readonly showCollapseItem = output<NavigationItem>();

  readonly visibleChildren = computed(() => {
    const children = this.item().children || [];
    const userRole = this.authService.currentUser()?.role;

    return children.filter((child) => {
      if (child.hidden === true) {
        return false;
      }
      if (child.roles && child.roles.length > 0) {
        if (!userRole || !child.roles.includes(userRole)) {
          return false;
        }
      }
      return true;
    });
  });

  current_url!: string;

  ngOnInit() {
    this.current_url = this.location.path();
    //eslint-disable-next-line
    //@ts-ignore
    const baseHref = this.location['_baseHref'] || '';
    this.current_url = baseHref + this.current_url;

    setTimeout(() => {
      const links = document.querySelectorAll('a.nav-link') as NodeListOf<HTMLAnchorElement>;
      links.forEach((link: HTMLAnchorElement) => {
        if (link.getAttribute('href') === this.current_url) {
          this.activateParentMenu(link);
        }
      });
    }, 0);
  }

  activateParentMenu(element: HTMLAnchorElement) {
    let parent = element.parentElement;
    while (parent && parent.classList) {
      if (parent.classList.contains('coded-hasmenu')) {
        parent.classList.add('coded-trigger');
        parent.classList.add('active');
      }
      parent = parent.parentElement;
    }
  }

  subMenuCollapse(item: NavigationItem) {
    this.showCollapseItem.emit(item);
  }
}
