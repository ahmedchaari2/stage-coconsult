import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

/**
 * beforeDismiss d'un NgbModal : ESC et clic sur le fond ne redemandent confirmation que si le
 * formulaire a été modifié, sinon ferment directement. N'intercepte pas le bouton Annuler du
 * template (ng-bootstrap n'appelle beforeDismiss que pour ESC/backdrop, jamais pour un
 * modal.dismiss() appelé depuis le code) : un clic explicite sur Annuler reste immédiat.
 */
export function confirmDiscardIfDirty(form: { dirty: boolean | null }, confirmMessage: string): boolean {
  if (!form.dirty) {
    return true;
  }
  return window.confirm(confirmMessage);
}

export function focusFirstField(modalRef: NgbModalRef, elementId: string): void {
  modalRef.shown.subscribe(() => {
    document.getElementById(elementId)?.focus();
  });
}

export function focusFirstInvalidField(): void {
  setTimeout(() => {
    document.querySelector<HTMLElement>('.modal.show .is-invalid')?.focus();
  });
}
