/**
 * Sexe du patient — valeurs exactes de l'enum backend
 * (tn.coconsult.medtrack.patient.model.SexePatient), sérialisé en
 * @Enumerated(EnumType.STRING) : le nom de la constante Java est envoyé/reçu tel quel.
 */
export type SexePatient = 'HOMME' | 'FEMME';

export interface Patient {
  id: number;
  nom: string;
  prenom: string;
  dateNaissance: string; // ISO date "YYYY-MM-DD"
  telephone: string;
  email: string;
  adresse: string;
  medecinReferentId: number | null;
  sexe?: SexePatient | null;
  contactUrgenceNom?: string | null;
  contactUrgenceTelephone?: string | null;
  cin?: string | null;
  /** Numéro de dossier lisible (PAT-{année}-{séquence}), généré côté serveur : jamais soumis par le client. */
  numeroDossier?: string | null;
  numeroAssurance?: string | null;
  archived?: boolean;
  archivedAt?: string | null; // date/heure d'archivage (ISO), présent uniquement si archived === true
}

/**
 * Payload pour création/mise à jour (POST/PUT /api/patients)
 * Champs requis par l'API : nom, prenom, dateNaissance, telephone, email, adresse
 * medecinReferentId est optionnel (nullable côté backend). sexe/contactUrgence(Nom|Telephone)/cin/
 * numeroAssurance sont optionnels également ; numeroDossier est absent volontairement
 * (généré côté serveur, jamais fourni par le client).
 */
export interface PatientRequest {
  nom: string;
  prenom: string;
  dateNaissance: string; // ISO date "YYYY-MM-DD"
  telephone: string;
  email: string;
  adresse: string;
  medecinReferentId?: number | null;
  sexe?: SexePatient | null;
  contactUrgenceNom?: string | null;
  contactUrgenceTelephone?: string | null;
  cin?: string | null;
  numeroAssurance?: string | null;
}
