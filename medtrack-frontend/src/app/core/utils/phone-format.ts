export function formatPhoneDisplay(value: string | null | undefined): string {
  if (!value) return '';
  const trimmed = value.trim();
  const digits = value.replace(/\D/g, '');
  if (!digits) return value;

  if (digits.length === 8) {
    return formatTunisianSubscriberNumber(digits);
  }

  const tunisianDigits = digits.startsWith('00216') ? digits.slice(5) : digits.startsWith('216') ? digits.slice(3) : null;
  if (tunisianDigits?.length === 8) {
    return `+216 ${formatTunisianSubscriberNumber(tunisianDigits)}`;
  }

  return trimmed;
}

function formatTunisianSubscriberNumber(digits: string): string {
  return `${digits.slice(0, 2)} ${digits.slice(2, 5)} ${digits.slice(5, 8)}`;
}
