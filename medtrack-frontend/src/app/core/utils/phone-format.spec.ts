import { describe, expect, it } from 'vitest';

import { formatPhoneDisplay } from './phone-format';

describe('formatPhoneDisplay', () => {
  it('formats a local Tunisian number', () => {
    expect(formatPhoneDisplay('70123456')).toBe('70 123 456');
  });

  it('formats a Tunisian number with the +216 country code', () => {
    expect(formatPhoneDisplay('+21670000001')).toBe('+216 70 000 001');
    expect(formatPhoneDisplay('+216 70 000 001')).toBe('+216 70 000 001');
  });

  it('normalizes the 00216 international prefix for display', () => {
    expect(formatPhoneDisplay('00216 20 123 456')).toBe('+216 20 123 456');
  });

  it('leaves unsupported international formats unchanged', () => {
    expect(formatPhoneDisplay('+33 1 23 45 67 89')).toBe('+33 1 23 45 67 89');
  });
});
