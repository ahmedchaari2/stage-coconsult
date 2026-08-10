import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { AppNotification, AppointmentNotificationPayload } from '../models/notification.model';
import { PageDTO } from '../models/page.model';
import { environment } from 'src/environments/environment';

export type ToastType = 'success' | 'danger' | 'warning';

export interface Toast {
  id: number;
  message: string;
  type: ToastType;
}

/** Nombre de notifications conservées en mémoire (borne la liste sur une session très longue). */
const MAX_NOTIFICATIONS = 200;
const PAGE_SIZE = 10;

/**
 * Forme d'un élément de GET /api/notifications : le backend sérialise le booléen d'état de
 * lecture sous la clé `lu`, pas `read`. `AppNotification.read` est un nom frontend uniquement ;
 * le mapper `toAppNotification` ci-dessous fait la conversion.
 */
interface NotificationApiItem extends AppointmentNotificationPayload {
  lu: boolean;
}

function toAppNotification(item: NotificationApiItem): AppNotification {
  return { ...item, read: item.lu };
}

/**
 * Service centralisé de notifications : toasts (NgbToast via ToastContainerComponent, purement
 * local) ET notifications applicatives persistées côté backend (cloche du header), combinant
 * historique (GET /api/notifications, GET /api/notifications/unread-count) et flux temps réel
 * (alimenté par NotificationWebsocketService). Fournit des signaux privés + des accès publics
 * en lecture seule pour l'affichage.
 */
@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/notifications`;

  private readonly _toasts = signal<Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();

  private readonly _notifications = signal<AppNotification[]>([]);
  readonly notifications = this._notifications.asReadonly();

  // car seule la première page est chargée en mémoire — compter localement sous-estimerait le
  private readonly _unreadCount = signal(0);
  readonly unreadCount = this._unreadCount.asReadonly();

  // Pagination de l'historique (bouton "Charger plus" du dropdown, format compact) :
  // app-pagination serait trop large pour ce contexte, un simple "charger plus" suffit ici.
  private readonly _page = signal(0);
  private readonly _hasMore = signal(false);
  readonly hasMore = this._hasMore.asReadonly();
  private readonly _loadingMore = signal(false);
  readonly loadingMore = this._loadingMore.asReadonly();

  private nextId = 0;

  /**
   * Charge la première page d'historique + le compteur non-lu (GET /api/notifications?page=0 et
   * GET /api/notifications/unread-count), pour initialiser l'état avant toute connexion
   * WebSocket — voir AuthService.login()/bootstrapSession(). Best-effort : un échec réseau laisse
   * l'état vide plutôt que de bloquer l'authentification (une notification est une amélioration,
   * jamais une condition de démarrage).
   */
  loadInitial(): Observable<void> {
    return this.http.get<PageDTO<NotificationApiItem>>(this.API_URL, { params: { page: 0, size: PAGE_SIZE } }).pipe(
      tap((data) => {
        this._notifications.set(data.content.map(toAppNotification));
        this._page.set(data.page);
        this._hasMore.set(data.page + 1 < data.totalPages);
      }),
      // d'afficher l'historique déjà récupéré (l'un ne dépend pas de l'autre côté backend).
      tap(() => {
        this.http
          .get<number>(`${this.API_URL}/unread-count`)
          .pipe(catchError(() => of(0)))
          .subscribe((count) => this._unreadCount.set(count));
      }),
      map(() => void 0),
      catchError(() => {
        this._notifications.set([]);
        this._page.set(0);
        this._hasMore.set(false);
        return of(void 0);
      })
    );
  }

  loadMore(): void {
    if (!this._hasMore() || this._loadingMore()) {
      return;
    }

    this._loadingMore.set(true);
    const nextPage = this._page() + 1;

    this.http.get<PageDTO<NotificationApiItem>>(this.API_URL, { params: { page: nextPage, size: PAGE_SIZE } }).subscribe({
      next: (data) => {
        this._notifications.update((list) => {
          const knownIds = new Set(list.map((n) => n.id));
          return [...list, ...data.content.map(toAppNotification).filter((n) => !knownIds.has(n.id))];
        });
        this._page.set(data.page);
        this._hasMore.set(data.page + 1 < data.totalPages);
        this._loadingMore.set(false);
      },
      error: () => this._loadingMore.set(false)
    });
  }

  addAppointmentNotification(payload: AppointmentNotificationPayload): void {
    if (this._notifications().some((n) => n.id === payload.id)) {
      return;
    }
    const next: AppNotification = { ...payload, read: false };
    this._notifications.update((list) => [next, ...list].slice(0, MAX_NOTIFICATIONS));
    this._unreadCount.update((c) => c + 1);
  }

  /**
   * Marque toutes les notifications comme lues côté backend (PUT /api/notifications/read-all),
   * appelé à l'ouverture du dropdown de la cloche. Optimiste côté réseau : en cas d'échec, l'état
   * local n'est pas modifié (le badge reste visible, une nouvelle ouverture retentera).
   */
  markAllAsRead(): void {
    if (this._unreadCount() === 0) {
      return;
    }
    this.http.put<void>(`${this.API_URL}/read-all`, {}).subscribe({
      next: () => {
        this._notifications.update((list) => list.map((n) => (n.read ? n : { ...n, read: true })));
        this._unreadCount.set(0);
      },
      error: () => {
        /* best-effort : voir doc de la méthode */
      }
    });
  }

  markAsRead(id: number): void {
    const target = this._notifications().find((n) => n.id === id);
    if (!target || target.read) {
      return;
    }
    this.http.put<void>(`${this.API_URL}/${id}/read`, {}).subscribe({
      next: () => {
        this._notifications.update((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)));
        this._unreadCount.update((c) => Math.max(0, c - 1));
      },
      error: () => {
        /* best-effort : voir doc de la méthode */
      }
    });
  }

  /** Vide l'état (appelé à la déconnexion WebSocket/logout : pas de résidu entre deux comptes). */
  clearNotifications(): void {
    this._notifications.set([]);
    this._unreadCount.set(0);
    this._page.set(0);
    this._hasMore.set(false);
    this._loadingMore.set(false);
  }

  showError(message: string): void {
    this.push('danger', message);
  }

  showSuccess(message: string): void {
    this.push('success', message);
  }

  showWarning(message: string): void {
    this.push('warning', message);
  }

  remove(id: number): void {
    this._toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private push(type: ToastType, message: string): void {
    const id = ++this.nextId;
    this._toasts.update((list) => [...list, { id, message, type }]);
  }
}
