import { Directive, DestroyRef, HostListener, inject, input, OnInit, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

@Directive({
  selector: '[appDebounceInput]',
  standalone: true
})
export class DebounceInput implements OnInit {
  debounceMs = input(400);

  debouncedInput = output<string>();

  private readonly destroyRef = inject(DestroyRef);
  private readonly input$ = new Subject<string>();

  ngOnInit(): void {
    this.input$
      .pipe(debounceTime(this.debounceMs()), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.debouncedInput.emit(value));
  }

  @HostListener('input', ['$event'])
  onInput(event: Event): void {
    this.input$.next((event.target as HTMLInputElement).value);
  }
}
