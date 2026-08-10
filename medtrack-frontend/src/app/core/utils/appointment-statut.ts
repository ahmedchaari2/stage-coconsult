import { AppointmentStatut } from '../models/appointment.model';

export function appointmentStatutBadgeClass(statut: AppointmentStatut): string {
  switch (statut) {
    case 'PLANIFIE':
      return 'badge-statut-planifie';
    case 'CONFIRME':
      return 'badge-statut-confirme';
    case 'ANNULE':
      return 'badge-statut-annule';
    case 'HONORE':
      return 'badge-statut-honore';
  }
}

export function appointmentStatutIcon(statut: AppointmentStatut): { type: string; theme: 'outline' | 'fill' } {
  switch (statut) {
    case 'PLANIFIE':
      return { type: 'clock-circle', theme: 'outline' };
    case 'CONFIRME':
      return { type: 'check-circle', theme: 'outline' };
    case 'ANNULE':
      return { type: 'close-circle', theme: 'outline' };
    case 'HONORE':
      return { type: 'check-circle', theme: 'fill' };
  }
}
