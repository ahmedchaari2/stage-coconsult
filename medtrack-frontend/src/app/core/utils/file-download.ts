/**
 * Déclenche le téléchargement d'un Blob sous le nom donné. Le backend renvoie l'ordonnance en
 * `Content-Disposition: inline` (pratique pour l'ouvrir dans un onglet) : c'est donc ici, côté
 * client, que le fichier est forcé en téléchargement.
 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  // Libère l'URL objet une fois le téléchargement lancé, sinon le Blob reste en mémoire
  // jusqu'au rechargement de la page.
  URL.revokeObjectURL(url);
}
