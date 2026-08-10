import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpContext, HttpContextToken } from '@angular/common/http';
import { Observable, tap, map, catchError, finalize, share, of } from 'rxjs';
import {
  LoginRequest,
  AuthResponse,
  User,
  UpdateProfileRequest,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  ChangePasswordRequest,
  ChangePasswordResponse
} from '../models/user.model';
import { NotificationWebsocketService } from './notification-websocket.service';
import { NotificationService } from './notification.service';
import { environment } from 'src/environments/environment';

export const SKIP_AUTH = new HttpContextToken<boolean>(() => false);

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = `${environment.apiUrl}/auth`;
  private readonly USERS_API_URL = `${environment.apiUrl}/users`;
  private readonly http = inject(HttpClient);
  private readonly notificationWs = inject(NotificationWebsocketService);
  private readonly notificationService = inject(NotificationService);
  readonly currentUser = signal<User | null>(null);
  readonly isAuthenticated = signal<boolean>(false);

  private refreshInFlight: Observable<boolean> | null = null;

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/login`, request).pipe(
      tap((response) => {
        this.currentUser.set(this.toUser(response));
        this.isAuthenticated.set(true);
        this.initNotifications();
      })
    );
  }

  refreshToken(): Observable<boolean> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }

    const request$ = this.http
      .post<AuthResponse>(
        `${this.API_URL}/refresh`,
        {},
        {
          context: new HttpContext().set(SKIP_AUTH, true)
        }
      )
      .pipe(
        tap((response) => {
          if (response && response.id !== undefined) {
            this.currentUser.set(this.toUser(response));
          }
          this.isAuthenticated.set(true);
        }),
        map(() => true),
        share(),
        finalize(() => {
          this.refreshInFlight = null;
        })
      );

    this.refreshInFlight = request$;
    return request$;
  }

  bootstrapSession(): Observable<boolean> {
    return this.http
      .get<AuthResponse>(`${this.API_URL}/me`, {
        context: new HttpContext().set(SKIP_AUTH, true)
      })
      .pipe(
        tap((response) => {
          this.currentUser.set(this.toUser(response));
          this.isAuthenticated.set(true);
          this.initNotifications();
        }),
        map(() => true),
        catchError(() => {
          this.isAuthenticated.set(false);
          this.currentUser.set(null);
          return of(false);
        })
      );
  }

  logout(): void {
    this.http
      .post(
        `${this.API_URL}/logout`,
        {},
        {
          context: new HttpContext().set(SKIP_AUTH, true)
        }
      )
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.clearSession());
  }

  clearSession(): void {
    this.currentUser.set(null);
    this.isAuthenticated.set(false);
    this.notificationWs.disconnect();
  }

  updateOwnProfile(request: UpdateProfileRequest): Observable<User> {
    return this.http.put<AuthResponse>(`${this.USERS_API_URL}/me`, request).pipe(
      tap((response) => this.currentUser.set(this.toUser(response))),
      map((response) => this.toUser(response))
    );
  }

  forgotPassword(email: string): Observable<void> {
    const request: ForgotPasswordRequest = { email };
    return this.http.post<void>(`${this.API_URL}/forgot-password`, request);
  }

  resetPassword(request: ResetPasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/reset-password`, request);
  }

  changePassword(request: ChangePasswordRequest): Observable<ChangePasswordResponse> {
    return this.http.put<ChangePasswordResponse>(`${this.USERS_API_URL}/me/password`, request);
  }

  uploadProfilePhoto(file: File): Observable<User> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<AuthResponse>(`${this.USERS_API_URL}/me/photo`, formData).pipe(
      tap((response) => this.currentUser.set(this.toUser(response))),
      map((response) => this.toUser(response))
    );
  }

  deleteProfilePhoto(): Observable<User> {
    return this.http.delete<AuthResponse>(`${this.USERS_API_URL}/me/photo`).pipe(
      tap((response) => this.currentUser.set(this.toUser(response))),
      map((response) => this.toUser(response))
    );
  }

  getCurrentUser(): User | null {
    return this.currentUser();
  }

  isLoggedIn(): boolean {
    return this.isAuthenticated();
  }

  private initNotifications(): void {
    this.notificationService.loadInitial().subscribe(() => this.notificationWs.connect());
  }

  private toUser(response: AuthResponse): User {
    return {
      id: response.id,
      email: response.email,
      nom: response.nom,
      prenom: response.prenom,
      role: response.role,
      actif: response.actif,
      photoUrl: this.toAbsolutePhotoUrl(response.photoUrl),
      specialite: response.specialite,
      numeroOrdre: response.numeroOrdre,
      telephone: response.telephone,
      createdAt: response.createdAt
    };
  }

  private toAbsolutePhotoUrl(photoUrl: string | null | undefined): string | null {
    if (!photoUrl) {
      return null;
    }
    return photoUrl.startsWith('http') ? photoUrl : `${environment.apiOrigin}${photoUrl}`;
  }
}
