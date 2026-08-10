/**
 * Seuil "mobile" pour le panneau de filtres avancés (voir AdvancedFilters) : le breakpoint
 * Bootstrap md, distinct du seuil 1025px utilisé par la sidebar (admin-layout/nav-bar).
 */
export function isMobileViewport(): boolean {
  return window.innerWidth < 768;
}
