export function formatDateDisplay(value: string | null | undefined): string {
  if (!value) return '';
  const [year, month, day] = value.slice(0, 10).split('-');
  if (!year || !month || !day) return '';
  return `${day}/${month}/${year}`;
}

export function formatTimeDisplay(value: string | null | undefined): string {
  if (!value) return '';
  return /(\d{2}:\d{2})/.exec(value)?.[1] ?? '';
}

export function formatDateTimeDisplay(value: string | null | undefined): string {
  if (!value) return '';
  const date = formatDateDisplay(value);
  const time = formatTimeDisplay(value);
  return date && time ? `${date} ${time}` : date;
}
