package tn.coconsult.medtrack.user.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.service.AccesLogService;
import tn.coconsult.medtrack.user.config.JwtService;
import tn.coconsult.medtrack.user.mapper.UserMapper;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;
import tn.coconsult.medtrack.user.dto.UpdateUserRequest;
import tn.coconsult.medtrack.user.dto.UpdateProfileRequest;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class UserServiceAccessLogTests {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserMapper userMapper;
    @Mock JwtService jwtService;
    @Mock AuthService authService;
    @Mock AccesLogService accesLogService;

    @InjectMocks UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void explicitDoctorDetailReadIsAuditedWithoutInventingAPatient() {
        authenticateAdmin();
        User medecin = medecin();
        when(userRepository.findByIdAndRole(4L, Role.MEDECIN)).thenReturn(Optional.of(medecin));

        userService.getUserById(4L);

        verify(accesLogService).enregistrerAcces(
                1L, TypeRessource.MEDECIN, 4L, null, TypeAction.VIEW);
    }

    @Test
    void doctorActivationChangeIsAuditedAsStatusChange() {
        authenticateAdmin();
        User medecin = medecin();
        when(userRepository.findByIdAndRole(4L, Role.MEDECIN)).thenReturn(Optional.of(medecin));
        when(userRepository.save(medecin)).thenReturn(medecin);

        userService.toggleStatus(4L);

        verify(accesLogService).enregistrerAcces(
                1L, TypeRessource.MEDECIN, 4L, null, TypeAction.STATUS_CHANGE);
    }

    @Test
    void updatesDoctorProfessionalContactInformation() {
        authenticateAdmin();
        User medecin = medecin();
        medecin.setEmail("doctor@medtrack.tn");
        when(userRepository.findByIdAndRole(4L, Role.MEDECIN)).thenReturn(Optional.of(medecin));
        when(userRepository.save(medecin)).thenReturn(medecin);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setSpecialite(" Cardiologie ");
        request.setNumeroOrdre(" ORD-1234 ");
        request.setTelephone(" +216 20 123 456 ");

        userService.updateUserById(4L, request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("Cardiologie", captor.getValue().getSpecialite());
        assertEquals("ORD-1234", captor.getValue().getNumeroOrdre());
        assertEquals("+216 20 123 456", captor.getValue().getTelephone());
        verify(accesLogService).enregistrerAcces(
                1L, TypeRessource.MEDECIN, 4L, null, TypeAction.UPDATE);
    }

    @Test
    void doctorCanUpdateOwnProfessionalProfileWithoutChangingRoleOrStatus() {
        User medecin = medecin();
        medecin.setEmail("doctor@medtrack.tn");
        when(userRepository.findById(4L)).thenReturn(Optional.of(medecin));
        when(userRepository.save(medecin)).thenReturn(medecin);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNom("Chatti");
        request.setPrenom("Nabil");
        request.setEmail("doctor@medtrack.tn");
        request.setTelephone("+216 20 123 456");
        request.setSpecialite("Cardiologie");
        request.setNumeroOrdre("OM-2020-0142");

        userService.updateOwnProfile(4L, request);

        assertEquals("+216 20 123 456", medecin.getTelephone());
        assertEquals("Cardiologie", medecin.getSpecialite());
        assertEquals("OM-2020-0142", medecin.getNumeroOrdre());
        assertEquals(Role.MEDECIN, medecin.getRole());
        assertEquals(true, medecin.getActif());
    }

    @Test
    void adminOwnProfileIgnoresDoctorOnlyFields() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        admin.setActif(true);
        admin.setEmail("admin@medtrack.tn");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.save(admin)).thenReturn(admin);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNom("Principal");
        request.setPrenom("Admin");
        request.setEmail("admin@medtrack.tn");
        request.setTelephone("+216 70 000 000");
        request.setSpecialite("Cardiologie");
        request.setNumeroOrdre("SHOULD-NOT-BE-SAVED");

        userService.updateOwnProfile(1L, request);

        assertEquals("+216 70 000 000", admin.getTelephone());
        assertEquals(null, admin.getSpecialite());
        assertEquals(null, admin.getNumeroOrdre());
        assertEquals(Role.ADMIN, admin.getRole());
    }

    private void authenticateAdmin() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }

    private User medecin() {
        User medecin = new User();
        medecin.setId(4L);
        medecin.setRole(Role.MEDECIN);
        medecin.setActif(true);
        return medecin;
    }
}
