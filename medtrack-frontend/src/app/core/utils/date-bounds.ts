/** Date la plus ancienne raisonnable pour une naissance (ISO yyyy-MM-dd), pour borner le sélecteur année du champ date de naissance. */
export function minBirthDateIso(): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() - 120);
  return d.toISOString().slice(0, 10);
}
