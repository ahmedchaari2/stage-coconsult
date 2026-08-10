import { AppointmentStatut } from './appointment.model';

/**
 * Types d'événements de rendez-vous poussés par le backend sur `/user/queue/appointments`
 * (STOMP). CHANGEMENT_STATUT couvre toute transition de statut (CONFIRME/ANNULE/HONORE/...),
 * y compris une annulation : il n'y a pas de type dédié "annulation".
 */
export type AppointmentNotificationType = 'NOUVEAU_RENDEZ_VOUS' | 'CHANGEMENT_STATUT' | 'RAPPEL';

export interface AppointmentNotificationData {
  patientNom?: string;
  patientPrenom?: string;
  medecinNom?: string;
  medecinPrenom?: string;
  dateHeure?: string; // ISO LocalDateTime du rendez-vous concerné
  statut?: AppointmentStatut; // uniquement pour CHANGEMENT_STATUT (nouveau statut)
}

export interface AppointmentNotificationPayload {
  id: number;
  type: AppointmentNotificationType;
  appointmentId: number;
  createdAt: string; // ISO datetime d'émission (tri, affichage relatif "il y a 5 min")
  data: AppointmentNotificationData;
}

/**
 * Notification telle qu'utilisée côté frontend : le payload de l'événement + son état de
 * lecture, qui est une donnée serveur (PUT .../read, PUT .../read-all), pas un flag local.
 * `read` est un nom frontend uniquement — le backend sérialise ce champ `lu` (voir
 * Notification.java) ; la conversion se fait à la frontière API dans
 * NotificationService.toAppNotification, jamais par cast direct de la réponse HTTP.
 */
export interface AppNotification extends AppointmentNotificationPayload {
  read: boolean;
}
