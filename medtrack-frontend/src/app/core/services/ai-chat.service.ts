import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatRequest, ChatResponse } from '../models/ai-chat.model';
import { HTTP_TIMEOUT_OVERRIDE_MS } from '../interceptors/auth.interceptor';
import { environment } from 'src/environments/environment';

/**
 * Aligné sur le budget Gateway de /ai/** (30s, voir api-gateway.yml), pas les 7s par défaut :
 * l'agrégation du contexte patient puis l'appel Gemini (modèle "thinking" à latence variable)
 * peuvent légitimement prendre plusieurs secondes.
 */
const AI_CHAT_TIMEOUT_MS = 31000;

@Injectable({
  providedIn: 'root'
})
export class AiChatService {
  // Hors /api : route dédiée du Gateway vers ai-service (voir api-gateway.yml, Path=/ai/**).
  private readonly API_URL = `${environment.apiOrigin}/ai/chat`;
  private readonly http = inject(HttpClient);

  sendMessage(patientId: number, message: string, sessionId: string): Observable<ChatResponse> {
    const body: ChatRequest = { message, sessionId };
    return this.http.post<ChatResponse>(`${this.API_URL}/${patientId}`, body, {
      context: new HttpContext().set(HTTP_TIMEOUT_OVERRIDE_MS, AI_CHAT_TIMEOUT_MS)
    });
  }
}
