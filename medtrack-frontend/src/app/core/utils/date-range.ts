export function isDateRangeInvalid(from: string, to: string): boolean {
  return !!from && !!to && to < from;
}
