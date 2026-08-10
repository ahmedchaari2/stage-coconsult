package tn.coconsult.medtrack.user.dto;

/**
 * Résultat d'une authentification renvoyé par le service au controller.
 * Contient les infos utilisateur ({@link LoginResponse}, destinées au body de réponse)
 * ainsi que les deux JWT (access + refresh), qui sont posés chacun dans un cookie
 * httpOnly par le controller — aucun token n'est exposé dans le corps de la réponse.
 */
public record LoginResult(LoginResponse loginResponse, String accessToken, String refreshToken) {
}
