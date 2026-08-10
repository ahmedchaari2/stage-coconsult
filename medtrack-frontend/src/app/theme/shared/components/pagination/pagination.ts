import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslocoModule } from '@jsverse/transloco';

@Component({
  selector: 'app-pagination',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoModule],
  templateUrl: './pagination.html',
  styleUrl: './pagination.scss'
})
export class Pagination {
  private static nextInstanceId = 0;

  readonly pageSizeSelectId = `pageSizeSelect-${++Pagination.nextInstanceId}`;

  page = input(0);
  totalPages = input(0);
  totalElements = input(0);
  size = input(10);
  sizeOptions = input<number[]>([10, 25, 50, 100]);

  pageChange = output<number>();
  sizeChange = output<number>();

  readonly ellipsis = -1;

  rangeStart = computed(() => (this.totalElements() === 0 ? 0 : this.page() * this.size() + 1));

  rangeEnd = computed(() => Math.min((this.page() + 1) * this.size(), this.totalElements()));

  pagerItems = computed<number[]>(() => {
    const total = this.totalPages();
    const current = this.page();

    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i);
    }

    const kept = new Set<number>([0, total - 1]);
    for (let p = current - 1; p <= current + 1; p++) {
      if (p >= 0 && p < total) {
        kept.add(p);
      }
    }

    const sorted = [...kept].sort((a, b) => a - b);
    const items: number[] = [];
    let previous: number | null = null;
    for (const p of sorted) {
      if (previous !== null && p - previous > 1) {
        items.push(this.ellipsis);
      }
      items.push(p);
      previous = p;
    }
    return items;
  });

  goTo(target: number): void {
    if (target < 0 || target >= this.totalPages() || target === this.page()) {
      return;
    }
    this.pageChange.emit(target);
  }

  onSizeChange(value: number): void {
    if (value !== this.size()) {
      this.sizeChange.emit(value);
    }
  }
}
