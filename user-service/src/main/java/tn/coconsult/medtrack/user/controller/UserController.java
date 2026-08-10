package tn.coconsult.medtrack.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tn.coconsult.medtrack.common.dto.MedecinSummaryResponse;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.user.config.JwtCookieUtils;
import tn.coconsult.medtrack.user.dto.AdminUserCreateRequest;
import tn.coconsult.medtrack.user.dto.ChangePasswordRequest;
import tn.coconsult.medtrack.user.dto.MedecinOptionResponse;
import tn.coconsult.medtrack.user.dto.UpdateProfileRequest;
import tn.coconsult.medtrack.user.dto.UpdateUserRequest;
import tn.coconsult.medtrack.user.dto.UserFilter;
import tn.coconsult.medtrack.user.dto.UserResponse;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.service.ProfilePhotoService;
import tn.coconsult.medtrack.user.service.UserService;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ProfilePhotoService profilePhotoService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        UserResponse createdUser = userService.createUserByAdmin(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdUser);
    }

    // Identité prise du SecurityContext, jamais d'un id client : impossible de modifier le
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> updateOwnProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        UserResponse response = userService.updateOwnProfile(currentUser.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request,
                                                   HttpServletRequest httpRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        String refreshTokenCookie = JwtCookieUtils.extractRefreshToken(httpRequest);
        userService.changeOwnPassword(currentUser.getId(), request, refreshTokenCookie);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> uploadOwnPhoto(@RequestParam("file") MultipartFile file) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        UserResponse response = profilePhotoService.upload(currentUser.getId(), file);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me/photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> deleteOwnPhoto() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        UserResponse response = profilePhotoService.delete(currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/medecins")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageDTO<UserResponse>> getMedecins(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nom,
            @RequestParam(required = false) String prenom,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String specialite,
            @RequestParam(required = false) String numeroOrdre,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean sansSpecialite,
            @RequestParam(required = false) Boolean sansNumeroOrdre,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        UserFilter filter = new UserFilter(nom, prenom, email, specialite, numeroOrdre, actif, q, sansSpecialite, sansNumeroOrdre);
        PageDTO<UserResponse> medecins = userService.searchMedecins(filter, page, size, sort, direction);
        return ResponseEntity.ok(medecins);
    }

    @GetMapping("/medecins/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MedecinOptionResponse>> getAllMedecinsOptions() {
        return ResponseEntity.ok(userService.findActiveMedecinsOptions());
    }

    /**
     * Même liste que /medecins/all, mais pour un appel service-à-service (dashboard-service, vue
     * admin : activité par médecin et qualité des données). Sans @PreAuthorize et déclaré permitAll
     * dans SecurityConfig, pour la même raison que /{id}/summary : l'appelant est un autre service,
     * qui ne porte pas le cookie JWT attendu par JwtAuthFilter. Ne renvoie que l'identité
     * professionnelle (nom, spécialité, n° d'ordre), pas les comptes complets.
     */
    @GetMapping("/internal/medecins")
    public ResponseEntity<List<MedecinOptionResponse>> getInternalMedecins() {
        return ResponseEntity.ok(userService.findAllMedecinsOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        UserResponse medecin = userService.getUserById(id);
        return ResponseEntity.ok(medecin);
    }

    /**
     * Vue réduite d'un médecin pour les appels service-à-service (en-tête d'ordonnance généré
     * par prescription-service), sur le modèle de /api/consultations/{id}/summary. Volontairement
     * plus pauvre que GET /{id} : ni email, ni statut, ni photo, uniquement ce qui s'imprime sur
     * une ordonnance.
     * <p>
     * Sans @PreAuthorize, contrairement au reste du contrôleur : l'appelant est un autre service,
     * qui ne porte pas le cookie JWT attendu par JwtAuthFilter et n'a donc aucun rôle ici. Le
     * chemin est déclaré permitAll dans SecurityConfig, où le compromis est détaillé.
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<MedecinSummaryResponse> getMedecinSummary(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getMedecinSummary(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        UserResponse medecin = userService.updateUserById(id, request);
        return ResponseEntity.ok(medecin);
    }

    @PutMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> toggleUserStatus(@PathVariable Long id) {
        UserResponse medecin = userService.toggleStatus(id);
        return ResponseEntity.ok(medecin);
    }
}
