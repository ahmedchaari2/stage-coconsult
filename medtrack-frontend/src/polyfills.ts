/**
 * sockjs-client (utilisé par NotificationWebsocketService) référence `global` dès le chargement
 * de son module, avant tout code applicatif. Webpack le shimmait automatiquement, mais le builder
 * esbuild actuel non : sans ça on a un `ReferenceError: global is not defined` à l'évaluation du
 * bundle, avant même bootstrapApplication(), donc page blanche.
 *
 * Doit rester dans `polyfills` (angular.json) et pas en tête de main.ts : ce fichier tourne dans
 * un chunk chargé avant le bundle principal, alors qu'un `window.global = window` dans main.ts
 * s'exécuterait trop tard (les imports ES sont résolus avant le corps du module).
 */
(window as unknown as { global: Window }).global = window;

/**
 * ng-bootstrap utilise des attributs i18n-* en interne (labels "select month"/"previous month"
 * de NgbDatepickerNavigation), qu'Angular compile en appels à $localize — normalement fourni par
 * @angular/localize. Sans ça, ouvrir un calendrier plantait avec "$localize is not defined" et
 * l'en-tête du datepicker restait vide.
 *
 * On n'a pas de traductions pour ces labels (les jours/mois affichés sont gérés à part par
 * AppDatepickerI18n), donc $localize.translate n'est jamais défini et seul le chemin "pas de
 * traduction" nous intéresse : retirer le bloc de métadonnées (":meaning|description@@id:") en
 * tête du message et recoller le texte source. Autant réimplémenter juste ça plutôt que d'ajouter
 * toute la dépendance @angular/localize.
 */
function stripMetadataBlock(cooked: string, raw: string): string {
  if (raw.charAt(0) !== ':') {
    return cooked;
  }
  for (let cookedIndex = 1, rawIndex = 1; cookedIndex < cooked.length; cookedIndex++, rawIndex++) {
    if (raw[rawIndex] === '\\') {
      rawIndex++;
    } else if (cooked[cookedIndex] === ':') {
      return cooked.substring(cookedIndex + 1);
    }
  }
  throw new Error(`Unterminated $localize metadata block in "${raw}".`);
}

(window as unknown as { $localize: (messageParts: TemplateStringsArray, ...expressions: unknown[]) => string }).$localize = (
  messageParts: TemplateStringsArray,
  ...expressions: unknown[]
): string => {
  let message = stripMetadataBlock(messageParts[0], messageParts.raw[0]);
  for (let i = 1; i < messageParts.length; i++) {
    message += expressions[i - 1] + stripMetadataBlock(messageParts[i], messageParts.raw[i]);
  }
  return message;
};
