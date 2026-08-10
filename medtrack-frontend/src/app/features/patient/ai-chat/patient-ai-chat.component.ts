import { Component, ElementRef, HostListener, ViewChild, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import { MessageOutline, SendOutline, CloseOutline, RobotOutline } from '@ant-design/icons-angular/icons';
import { AiChatService } from 'src/app/core/services/ai-chat.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';

type ChatRole = 'user' | 'assistant' | 'error';

interface ChatEntry {
  role: ChatRole;
  text: string;
}

interface Suggestion {
  label: string;
  question: string;
}

/**
 * Widget flottant, indépendant de l'onglet actif de la fiche patient : reste accessible qu'on
 * soit sur Informations, Dossier médical ou Historique. Conversation en mémoire uniquement,
 * perdue à la fermeture/au changement de patient — aucune persistance côté serveur.
 */
@Component({
  selector: 'app-patient-ai-chat',
  standalone: true,
  imports: [CommonModule, TranslocoModule, IconDirective],
  templateUrl: './patient-ai-chat.component.html',
  styleUrl: './patient-ai-chat.component.scss'
})
export class PatientAiChatComponent {
  private readonly aiChatService = inject(AiChatService);
  private readonly authService = inject(AuthService);
  private readonly translocoService = inject(TranslocoService);
  private readonly iconService = inject(IconService);

  constructor() {
    this.iconService.addIcon(MessageOutline, SendOutline, CloseOutline, RobotOutline);
  }

  patientId = input.required<number>();

  readonly isVisible = computed(() => {
    const role = this.authService.currentUser()?.role;
    return role === 'ADMIN' || role === 'MEDECIN';
  });

  // Libellé court affiché sur la puce, question complète envoyée au clic : un bouton avec la
  // phrase entière prend toute la largeur du panneau et casse sur deux lignes.
  readonly suggestions = computed<Suggestion[]>(() => [
    this.suggestion('summary'),
    this.suggestion('allergiesTreatment'),
    this.suggestion('activePrescriptions'),
    this.suggestion('lastDiagnostic'),
    this.suggestion('nextAppointment')
  ]);

  isOpen = signal(false);
  isSending = signal(false);
  entries = signal<ChatEntry[]>([]);

  // Stable pour la durée de vie du composant (donc du patient affiché) : c'est cette clé qui
  // laisse ai-service retrouver l'historique de la conversation entre deux questions.
  private readonly sessionId = crypto.randomUUID();

  @ViewChild('messageInput') private messageInputRef?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('scrollAnchor') private scrollAnchorRef?: ElementRef<HTMLDivElement>;

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.isOpen()) this.close();
  }

  toggle(): void {
    this.isOpen.update((open) => !open);
    if (this.isOpen()) {
      this.focusInput();
    }
  }

  close(): void {
    this.isOpen.set(false);
  }

  sendSuggestion(question: string): void {
    this.send(question);
  }

  onSubmit(): void {
    const input = this.messageInputRef?.nativeElement;
    const text = input?.value.trim();
    if (!text) return;
    this.send(text);
  }

  onEnter(event: Event): void {
    event.preventDefault();
    this.onSubmit();
  }

  private send(text: string): void {
    if (this.isSending()) return;

    this.entries.update((list) => [...list, { role: 'user', text }]);
    this.clearInput();
    this.isSending.set(true);
    this.scrollToBottom();

    this.aiChatService.sendMessage(this.patientId(), text, this.sessionId).subscribe({
      next: (response) => {
        this.entries.update((list) => [...list, { role: 'assistant', text: response.reponse }]);
        this.isSending.set(false);
        this.focusInput();
        this.scrollToBottom();
      },
      error: (err: HttpErrorResponse) => {
        this.entries.update((list) => [...list, { role: 'error', text: this.errorMessage(err) }]);
        this.isSending.set(false);
        this.focusInput();
        this.scrollToBottom();
      }
    });
  }

  private errorMessage(err: HttpErrorResponse): string {
    if (err.status === 403) {
      return this.translocoService.translate('patient.aiChat.errors.forbidden');
    }
    return (
      extractBackendErrorMessage(err) ??
      this.translocoService.translate(err.status === 503 ? 'patient.aiChat.errors.unavailable' : 'patient.aiChat.errors.generic')
    );
  }

  private suggestion(key: string): Suggestion {
    return {
      label: this.translocoService.translate(`patient.aiChat.suggestions.${key}Label`),
      question: this.translocoService.translate(`patient.aiChat.suggestions.${key}`)
    };
  }

  private clearInput(): void {
    const input = this.messageInputRef?.nativeElement;
    if (input) input.value = '';
  }

  private focusInput(): void {
    setTimeout(() => this.messageInputRef?.nativeElement.focus());
  }

  private scrollToBottom(): void {
    setTimeout(() => this.scrollAnchorRef?.nativeElement.scrollIntoView({ block: 'end' }));
  }
}
