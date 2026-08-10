import { TranslocoService } from '@jsverse/transloco';
import { AppointmentNotificationPayload } from '../models/notification.model';
import { formatDateTimeDisplay } from './date-format';

/**
 * Traduit une notification de rendez-vous à partir de ses données structurées (type + data) —
 * jamais d'une phrase déjà formée par le backend, voir `notifications.appointment.*` dans
 * fr.json et en.json. Réutilisée à la fois par le toast affiché à la réception WS et par le
 * dropdown de la cloche (nav-right).
 */
export function formatAppointmentNotification(
  payload: Pick<AppointmentNotificationPayload, 'type' | 'data'>,
  transloco: TranslocoService
): string {
  // soft delete), ou champs patient/médecin introuvables côté backend. Chaque champ retombe sur
  const data = payload.data ?? {};

  const patientName = [data.patientNom, data.patientPrenom].filter(Boolean).join(' ');
  const medecinName = [data.medecinNom, data.medecinPrenom].filter(Boolean).join(' ');

  return transloco.translate(`notifications.appointment.${payload.type}`, {
    patient: patientName || transloco.translate('notifications.appointment.fallbackPatient'),
    medecin: medecinName || transloco.translate('notifications.appointment.fallbackMedecin'),
    date: formatNotificationDateTime(data.dateHeure) || transloco.translate('notifications.appointment.fallbackDate'),
    statut: data.statut
      ? transloco.translate(`appointment.statut.${data.statut}`)
      : transloco.translate('notifications.appointment.fallbackStatut')
  });
}

export function formatRelativeTime(createdAt: string, transloco: TranslocoService): string {
  const diffMs = Date.now() - new Date(createdAt).getTime();
  const diffMin = Math.floor(diffMs / 60000);

  if (diffMin < 1) return transloco.translate('notifications.relativeTime.now');
  if (diffMin < 60) return transloco.translate('notifications.relativeTime.minutes', { count: diffMin });

  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return transloco.translate('notifications.relativeTime.hours', { count: diffH });

  const diffD = Math.floor(diffH / 24);
  return transloco.translate('notifications.relativeTime.days', { count: diffD });
}

function formatNotificationDateTime(value?: string): string {
  if (!value) return '';
  return formatDateTimeDisplay(value) || value;
}
