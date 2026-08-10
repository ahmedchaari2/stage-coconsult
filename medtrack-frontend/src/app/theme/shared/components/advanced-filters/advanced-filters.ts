import { Component, computed, inject, input, model, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslocoModule } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import { FilterOutline, SearchOutline } from '@ant-design/icons-angular/icons';

import { DebounceInput } from '../../directives/debounce-input';

export interface FilterChip {
  key: string;
  label: string;
  value: string;
}

@Component({
  selector: 'app-advanced-filters',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoModule, IconDirective, DebounceInput],
  templateUrl: './advanced-filters.html',
  styleUrl: './advanced-filters.scss'
})
export class AdvancedFilters {
  quickSearchValue = input('');
  quickSearchPlaceholder = input<string | null>(null);
  activeFilterCount = input(0);
  chips = input<FilterChip[]>([]);

  quickSearchValueChange = output<string>();
  quickSearchDebounced = output<string>();
  chipRemove = output<string>();
  resetAll = output<void>();
  applyRequested = output<void>();

  panelOpen = model(false);

  readonly hasAnythingActive = computed(() => this.activeFilterCount() > 0 || this.quickSearchValue().length > 0);

  constructor() {
    inject(IconService).addIcon(FilterOutline, SearchOutline);
  }

  clearQuickSearch(): void {
    this.quickSearchValueChange.emit('');
    this.quickSearchDebounced.emit('');
  }

  applyAndClose(): void {
    this.panelOpen.set(false);
    this.applyRequested.emit();
  }
}
