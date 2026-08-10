import { Component, inject, OnInit, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { SpinnerComponent } from 'src/app/theme/shared/components/spinner/spinner.component';
import { Spinkit } from 'src/app/theme/shared/components/spinner/spinkits';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { MedecinEditModalComponent } from 'src/app/features/medecin/edit-modal/medecin-edit-modal';
import { Medecin } from 'src/app/core/models/medecin.model';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { MedecinStatutBadgeComponent } from 'src/app/theme/shared/components/medecin-statut-badge/medecin-statut-badge';
import { EmailLinkComponent } from 'src/app/theme/shared/components/email-link/email-link';
import { PhoneLinkComponent } from 'src/app/theme/shared/components/phone-link/phone-link';
import { DateFormatPipe } from 'src/app/core/pipes/date-format.pipe';

@Component({
  selector: 'app-medecin-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    CardComponent,
    SpinnerComponent,
    MedecinEditModalComponent,
    TranslocoModule,
    MedecinStatutBadgeComponent,
    EmailLinkComponent,
    PhoneLinkComponent,
    DateFormatPipe
  ],
  templateUrl: './medecin-detail.html',
  styleUrl: './medecin-detail.scss'
})
export class MedecinDetail implements OnInit {
  private readonly medecinService = inject(MedecinService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly translocoService = inject(TranslocoService);

  spinkit = Spinkit.skLine;

  medecin = signal<Medecin | null>(null);
  isLoading = signal(true);
  error = signal<string | null>(null);
  medecinId = signal<number | null>(null);

  @ViewChild(MedecinEditModalComponent) private editModalCmp!: MedecinEditModalComponent;

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const idParam = params.get('id');
      if (idParam) {
        const id = +idParam;
        this.medecinId.set(id);
        this.loadMedecin(id);
      } else {
        this.error.set(this.translocoService.translate('medecin.errors.idMissing'));
        this.isLoading.set(false);
      }
    });
  }

  loadMedecin(id: number): void {
    this.isLoading.set(true);
    this.error.set(null);

    this.medecinService.getMedecin(id).subscribe({
      next: (data) => {
        this.medecin.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.error.set(
          this.translocoService.translate(
            err.status === 404
              ? 'medecin.errors.notFound'
              : err.status === 401
                ? 'common.errors.unauthorized'
                : err.status === 403
                  ? 'common.errors.adminRequired'
                  : 'medecin.errors.loadOneFailed'
          )
        );
        this.isLoading.set(false);
      }
    });
  }

  onEdit(): void {
    const m = this.medecin();
    if (!m) return;
    this.editModalCmp.open(m);
  }

  /** Le backend renvoie le médecin à jour : remplace directement l'affichage, sans re-fetch. */
  onMedecinSaved(updated: Medecin): void {
    this.medecin.set(updated);
  }

  onBack(): void {
    this.router.navigate(['/medecins']);
  }

  get pageTitle(): string | null {
    const m = this.medecin();
    return m ? `${m.nom} ${m.prenom}` : null;
  }
}
