/** Statut calculé d'une prescription : consultation + durée comparée à aujourd'hui, jamais stocké. */
export type PrescriptionStatutCalcule = 'active' | 'expiree' | 'indetermine';

export function prescriptionStatutCalcule(
  consultationDate: string | null | undefined,
  dureeJours: number | null | undefined
): PrescriptionStatutCalcule {
  if (!consultationDate || dureeJours == null) return 'indetermine';
  const debut = new Date(consultationDate.slice(0, 10));
  const fin = new Date(debut);
  fin.setDate(fin.getDate() + dureeJours);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return fin >= today ? 'active' : 'expiree';
}
