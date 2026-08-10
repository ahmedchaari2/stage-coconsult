import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LayoutStateService {
  readonly navCollapsedMob = signal(false);

  toggleNavCollapsedMob() {
    this.navCollapsedMob.update((isOpen) => !isOpen);
  }

  openNavCollapsedMob() {
    this.navCollapsedMob.set(true);
  }

  closeNavCollapsedMob() {
    this.navCollapsedMob.set(false);
  }
}
