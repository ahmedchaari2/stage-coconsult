import { Directive, HostBinding, HostListener, input, output } from '@angular/core';

export interface SortState {
  field: string;
  direction: 'asc' | 'desc';
}

@Directive({
  selector: 'th[appSortableColumn]',
  standalone: true
})
export class SortableColumn {
  appSortableColumn = input.required<string>();
  sortState = input<SortState | null>(null);

  sortChange = output<SortState>();

  @HostBinding('class.sorted-asc')
  get isSortedAsc(): boolean {
    return this.sortState()?.field === this.appSortableColumn() && this.sortState()?.direction === 'asc';
  }

  @HostBinding('class.sorted-desc')
  get isSortedDesc(): boolean {
    return this.sortState()?.field === this.appSortableColumn() && this.sortState()?.direction === 'desc';
  }

  @HostBinding('attr.aria-sort')
  get ariaSort(): string {
    if (this.isSortedAsc) return 'ascending';
    if (this.isSortedDesc) return 'descending';
    return 'none';
  }

  @HostListener('click')
  onClick(): void {
    const field = this.appSortableColumn();
    const current = this.sortState();
    const direction: 'asc' | 'desc' = current?.field === field && current.direction === 'asc' ? 'desc' : 'asc';
    this.sortChange.emit({ field, direction });
  }
}
