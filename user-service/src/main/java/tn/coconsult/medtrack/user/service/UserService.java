package tn.coconsult.medtrack.user.service;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.service.AccesLogService;
import tn.coconsult.medtrack.common.dto.MedecinSummaryResponse;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.common.util.SortUtils;
import tn.coconsult.medtrack.user.config.JwtService;
import tn.coconsult.medtrack.user.dto.AdminUserCreateRequest;
import tn.coconsult.medtrack.user.dto.ChangePasswordRequest;
import tn.coconsult.medtrack.user.dto.MedecinOptionResponse;
import tn.coconsult.medtrack.user.dto.UpdateProfileRequest;
import tn.coconsult.medtrack.user.dto.UpdateUserRequest;
import tn.coconsult.medtrack.user.dto.UserFilter;
import tn.coconsult.medtrack.user.dto.UserResponse;
import tn.coconsult.medtrack.user.mapper.UserMapper;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;
import tn.coconsult.medtrack.user.repository.UserSpecifications;
import tn.coconsult.medtrack.user.util.EmailNormalizer;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final java.util.Map<String, String> MEDECIN_SORTABLE_FIELDS = java.util.Map.of(
            "nom", "nom",
            "prenom", "prenom",
            "email", "email",
            "specialite", "specialite",
            "numeroOrdre", "numeroOrdre",
            "createdAt", "createdAt");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final AuthService authService;
    private final AccesLogService accesLogService;

    /**
     * Recherche paginée de tous les médecins, portée globale pour tout ADMIN. invitedBy reste
     * en base comme donnée d'audit mais ne filtre plus les résultats.
     */
    public PageDTO<UserResponse> searchMedecins(UserFilter filter, int page, int size, String sort, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort resolvedSort = SortUtils.resolve(sort, direction, MEDECIN_SORTABLE_FIELDS, "createdAt", Sort.Direction.DESC);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, resolvedSort);

        Specification<User> spec = UserSpecifications.hasRole(Role.MEDECIN);
        Specification<User> dynamicSpec = UserSpecifications.build(filter);
        if (dynamicSpec != null) {
            spec = spec.and(dynamicSpec);
        }

        Page<User> result = userRepository.findAll(spec, pageRequest);
        return PageDTO.of(result, userMapper::toResponse);
    }

    public List<MedecinOptionResponse> findAllMedecinsOptions() {
        Specification<User> spec = UserSpecifications.hasRole(Role.MEDECIN);

        List<User> medecins = userRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "nom"));
        return medecins.stream().map(userMapper::toOptionResponse).toList();
    }

    public List<MedecinOptionResponse> findActiveMedecinsOptions() {
        Specification<User> spec = UserSpecifications.hasRole(Role.MEDECIN)
                .and(UserSpecifications.hasActif(true));
        return userRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "nom")).stream()
                .map(userMapper::toOptionResponse)
                .toList();
    }

    public UserResponse createUserByAdmin(AdminUserCreateRequest request) {
        if (request.getRole() == null || (request.getRole() != Role.ADMIN && request.getRole() != Role.MEDECIN)) {
            throw new IllegalArgumentException("Le rôle doit être ADMIN ou MEDECIN");
        }

        String email = EmailNormalizer.normalize(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Cet email est déjà utilisé");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setRole(request.getRole());
        user.setInvitedBy(null);
        user.setActif(true);
        user.setSpecialite(normalizeOptional(request.getSpecialite()));
        user.setNumeroOrdre(normalizeOptional(request.getNumeroOrdre()));
        user.setTelephone(normalizeOptional(request.getTelephone()));

        User savedUser = userRepository.save(user);
        if (savedUser.getRole() == Role.MEDECIN) {
            logMedecinAction(savedUser.getId(), TypeAction.CREATE);
        }
        return userMapper.toResponse(savedUser);
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findByIdAndRole(id, Role.MEDECIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable."));
        logMedecinAction(user.getId(), TypeAction.VIEW);
        return userMapper.toResponse(user);
    }

    public MedecinSummaryResponse getMedecinSummary(Long id) {
        User user = userRepository.findByIdAndRole(id, Role.MEDECIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable."));
        return new MedecinSummaryResponse(user.getNom(), user.getPrenom(), user.getSpecialite(), user.getNumeroOrdre());
    }

    public UserResponse updateUserById(Long id, UpdateUserRequest request) {
        User user = userRepository.findByIdAndRole(id, Role.MEDECIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable."));

        String newEmail = EmailNormalizer.normalize(request.getEmail());
        if (newEmail != null && !newEmail.equals(user.getEmail())) {
            if (userRepository.findByEmail(newEmail).isPresent()) {
                throw new IllegalArgumentException("Cet email est déjà utilisé");
            }
            user.setEmail(newEmail);
        }

        if (request.getNom() != null) {
            user.setNom(request.getNom());
        }
        if (request.getPrenom() != null) {
            user.setPrenom(request.getPrenom());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getActif() != null) {
            user.setActif(request.getActif());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getSpecialite() != null) {
            user.setSpecialite(normalizeOptional(request.getSpecialite()));
        }
        if (request.getNumeroOrdre() != null) {
            user.setNumeroOrdre(normalizeOptional(request.getNumeroOrdre()));
        }
        if (request.getTelephone() != null) {
            user.setTelephone(normalizeOptional(request.getTelephone()));
        }

        User saved = userRepository.save(user);
        logMedecinAction(saved.getId(), TypeAction.UPDATE);
        return userMapper.toResponse(saved);
    }

    /**
     * Met à jour le profil de l'utilisateur connecté : nom, prénom, email. userId vient du
     * SecurityContext (jamais du client), impossible de modifier le profil d'un autre.
     */
    public UserResponse updateOwnProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        String newEmail = EmailNormalizer.normalize(request.getEmail());
        if (!newEmail.equals(user.getEmail())) {
            userRepository.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est déjà utilisé");
                }
            });
            user.setEmail(newEmail);
        }

        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setTelephone(normalizeOptional(request.getTelephone()));
        if (user.getRole() == Role.MEDECIN) {
            user.setSpecialite(normalizeOptional(request.getSpecialite()));
            user.setNumeroOrdre(normalizeOptional(request.getNumeroOrdre()));
        }

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    /**
     * Change le mot de passe de l'utilisateur connecté (vérifie currentPassword par BCrypt),
     * puis révoque toutes les autres sessions sauf celle en cours (identifiée via le jti du
     * cookie refreshToken). Si le cookie est absent/illisible, aucune session "courante" n'est
     * identifiable et tout est révoqué.
     */
    public void changeOwnPassword(Long userId, ChangePasswordRequest request, String refreshTokenCookie) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe actuel incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        String currentJti = null;
        if (refreshTokenCookie != null && !refreshTokenCookie.isBlank()) {
            try {
                currentJti = jwtService.extractJti(refreshTokenCookie);
            } catch (JwtException | IllegalArgumentException e) {
                // cookie illisible, tant pis, on révoquera tout
            }
        }
        authService.revokeAllForUserExceptCurrent(user, currentJti);
    }

    public UserResponse toggleStatus(Long id) {
        User user = userRepository.findByIdAndRole(id, Role.MEDECIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable."));

        user.setActif(!user.getActif());

        User saved = userRepository.save(user);
        logMedecinAction(saved.getId(), TypeAction.STATUS_CHANGE);
        return userMapper.toResponse(saved);
    }

    private void logMedecinAction(Long medecinId, TypeAction action) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        accesLogService.enregistrerAcces(
                currentUser.getId(), TypeRessource.MEDECIN, medecinId, null, action);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
