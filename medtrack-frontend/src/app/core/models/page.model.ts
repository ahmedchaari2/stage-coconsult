/**
 * Enveloppe de pagination générique renvoyée par les endpoints backend paginés.
 * `page` est 0-based (aligné sur la pagination Spring Data).
 */
export interface PageDTO<T> {
  content: T[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
}
