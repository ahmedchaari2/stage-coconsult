import { Injectable, inject } from '@angular/core';
import { Client, IMessage, ReconnectionTimeMode } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { TranslocoService } from '@jsverse/transloco';
import { NotificationService } from './notification.service';
import { AppointmentNotificationPayload } from '../models/notification.model';
import { formatAppointmentNotification } from '../utils/notification-message';
import { environment } from 'src/environments/environment';

@Injectable({
  providedIn: 'root'
})
export class NotificationWebsocketService {
  private readonly notificationService = inject(NotificationService);
  private readonly translocoService = inject(TranslocoService);

  private readonly WS_URL = environment.notificationsWsUrl;

  private client: Client | null = null;

  /**
   * Ouvre la connexion si elle n'est pas déjà active. Idempotent : AuthService peut
   * appeler connect() depuis login() ET bootstrapSession() sans jamais créer deux
   * connexions (ce qui dupliquerait chaque notification reçue).
   *
   * Appelée synchronement depuis le `tap()` de login()/bootstrapSession() — et
   * bootstrapSession() est attendue par `provideAppInitializer` dans main.ts avant
   * même le premier rendu Angular. Toute exception non rattrapée ici remonterait
   * dans l'Observable d'auth et ferait échouer l'appInitializer : l'app resterait
   * bloquée sur une page blanche. La connexion temps réel est une amélioration,
   * jamais une condition de démarrage : on isole donc tout le corps dans un
   * try/catch, et les échecs post-connexion (WS/STOMP) sont journalisés sans
   * jamais se propager (onWebSocketClose/onStompError, pas de throw).
   */
  connect(): void {
    if (this.client?.active) {
      return;
    }

    try {
      this.client = new Client({
        // Le cookie httpOnly d'authentification part automatiquement : SockJS active
        // WebSocket natif envoie toujours les cookies du domaine cible lors du handshake.
        webSocketFactory: () => new SockJS(this.WS_URL),
        // boucle agressive en cas de coupure prolongée du backend.
        reconnectDelay: 2000,
        maxReconnectDelay: 30000,
        reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
        onConnect: () => {
          this.client?.subscribe('/user/queue/appointments', (message: IMessage) => this.handleMessage(message));
        },
        // Erreurs de transport/protocole : journalisées, jamais levées (la
        onWebSocketError: (event) => console.warn('[NotificationWebsocketService] Erreur WebSocket :', event),
        onStompError: (frame) => console.warn('[NotificationWebsocketService] Erreur STOMP :', frame.headers['message']),
        debug: () => {}
      });

      this.client.activate();
    } catch (err) {
      console.error('[NotificationWebsocketService] Connexion WebSocket impossible, notifications temps réel désactivées :', err);
      this.client = null;
    }
  }

  disconnect(): void {
    const client = this.client;
    this.client = null;
    client?.deactivate();
    this.notificationService.clearNotifications();
  }

  private handleMessage(message: IMessage): void {
    let payload: AppointmentNotificationPayload;
    try {
      payload = JSON.parse(message.body);
    } catch {
      return;
    }

    this.notificationService.addAppointmentNotification(payload);
    this.notificationService.showSuccess(formatAppointmentNotification(payload, this.translocoService));
  }
}
