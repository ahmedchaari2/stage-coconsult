import { Component, input, inject, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { NavigationEnd, Router, RouterModule, Event } from '@angular/router';
import { TranslocoModule } from '@jsverse/transloco';

import { NavigationItem, NavigationItems } from 'src/app/theme/layouts/admin-layout/navigation/navigation';

import { IconService } from '@ant-design/icons-angular';
import { GlobalOutline, NodeExpandOutline } from '@ant-design/icons-angular/icons';

interface titleType {
  url: string | boolean | undefined;
  title: string;
  translate?: string;
  breadcrumbs: unknown;
  type: string;
  link?: string | undefined;
  description?: string | undefined;
  path?: string | undefined;
}

@Component({
  selector: 'app-breadcrumb',
  imports: [RouterModule, TranslocoModule],
  templateUrl: './breadcrumb.component.html',
  styleUrl: './breadcrumb.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BreadcrumbComponent {
  private route = inject(Router);
  private iconService = inject(IconService);
  private cdr = inject(ChangeDetectorRef);

  type = input<string>('theme1');
  readonly dashboard = input(true);
  readonly Component = input(false);

  navigations: NavigationItem[];
  breadcrumbList: Array<string> = [];
  navigationList!: titleType[];
  componentList!: titleType[];

  constructor() {
    this.navigations = NavigationItems;
    this.setBreadcrumb();
    this.iconService.addIcon(...[GlobalOutline, NodeExpandOutline]);
  }

  setBreadcrumb() {
    this.updateBreadcrumb(this.route.url);

    this.route.events.subscribe((router: Event) => {
      if (router instanceof NavigationEnd) {
        this.updateBreadcrumb(router.url);
      }
    });
  }

  updateBreadcrumb(activeLink: string) {
    // Compare path only : un onglet qui ajoute un query param (ex. /medecins?section=invitations)
    // reste la même page pour le fil d'Ariane, pas une page différente qui ne matche plus rien.
    const activeItem = this.filterNavigation(this.navigations, activeLink.split('?')[0]);

    this.navigationList = [];

    if (activeItem) {
      this.navigationList = [activeItem];
    }
    this.cdr.markForCheck();
  }

  filterNavigation(navItems: NavigationItem[], activeLink: string): titleType | null {
    for (const navItem of navItems) {
      if (navItem.type === 'item' && 'url' in navItem && navItem.url === activeLink) {
        return {
          url: navItem.url || true,
          title: navItem.title,
          translate: navItem.translate,
          link: navItem.link,
          description: navItem.description,
          path: navItem.path,
          breadcrumbs: 'breadcrumbs' in navItem ? navItem.breadcrumbs : true,
          type: navItem.type
        };
      }
      if ((navItem.type === 'group' || navItem.type === 'collapse') && 'children' in navItem) {
        const activeItem = this.filterNavigation(navItem.children!, activeLink);
        if (activeItem) {
          return activeItem;
        }
      }
    }
    return null;
  }

  isLink(url: string | boolean | undefined): url is string {
    return typeof url === 'string';
  }
}
