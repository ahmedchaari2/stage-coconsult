import { Component, computed, forwardRef, inject, input } from '@angular/core';
import {
  AbstractControl,
  FormsModule,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ControlValueAccessor,
  ValidationErrors,
  Validator
} from '@angular/forms';
import { NgbDateAdapter, NgbDateParserFormatter, NgbDateStruct, NgbDatepickerI18n, NgbDatepickerModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import { CalendarOutline } from '@ant-design/icons-angular/icons';
import { IsoDateAdapter } from './iso-date-adapter';
import { AppDateParserFormatter } from './app-date-parser-formatter';
import { AppDatepickerI18n } from './app-datepicker-i18n';

type DateDisabledPredicate = (date: NgbDateStruct, current?: { year: number; month: number }) => boolean;

@Component({
  selector: 'app-date-input',
  standalone: true,
  imports: [FormsModule, NgbDatepickerModule, IconDirective, TranslocoModule],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DateInputComponent), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => DateInputComponent), multi: true },
    { provide: NgbDateAdapter, useClass: IsoDateAdapter },
    { provide: NgbDateParserFormatter, useClass: AppDateParserFormatter },
    { provide: NgbDatepickerI18n, useClass: AppDatepickerI18n }
  ],
  templateUrl: './date-input.html'
})
export class DateInputComponent implements ControlValueAccessor, Validator {
  inputId = input<string | null>(null);
  size = input<'sm' | 'md'>('md');
  minDate = input<string | null>(null);
  maxDate = input<string | null>(null);
  dateDisabled = input<DateDisabledPredicate>(() => false);
  invalid = input<boolean | null>(false);
  readonly isInvalid = computed(() => !!this.invalid());

  readonly minDateStruct = computed(() => this.toStruct(this.minDate()));
  readonly maxDateStruct = computed(() => this.toStruct(this.maxDate()));

  value: string | null = null;
  disabled = false;

  private onChange: (value: string | null) => void = () => {};
  onTouched: () => void = () => {};

  constructor() {
    inject(IconService).addIcon(CalendarOutline);
  }

  writeValue(value: string | null): void {
    this.value = value;
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  onValueChange(value: string | null): void {
    this.value = value;
    this.onChange(value);
  }

  validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value as string | null;
    if (!value) return null;
    const min = this.minDate();
    const max = this.maxDate();
    if (min && value < min) return { minDate: true };
    if (max && value > max) return { maxDate: true };
    const date = this.toStruct(value);
    if (date && this.dateDisabled()?.(date)) return { dateDisabled: true };
    return null;
  }

  private toStruct(iso: string | null): NgbDateStruct | null {
    if (!iso) return null;
    const [year, month, day] = iso.split('-').map(Number);
    if (!year || !month || !day) return null;
    return { year, month, day };
  }
}
