import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { Pagination } from './pagination';

describe('Pagination', () => {
  it('uses a unique page-size control ID for each instance', () => {
    const first = TestBed.runInInjectionContext(() => new Pagination());
    const second = TestBed.runInInjectionContext(() => new Pagination());

    expect(first.pageSizeSelectId).not.toBe(second.pageSizeSelectId);
  });
});
