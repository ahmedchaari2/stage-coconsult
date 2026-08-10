package tn.coconsult.medtrack.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.user.dto.UserResponse;
import tn.coconsult.medtrack.user.mapper.UserMapper;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfilePhotoService {

    private static final long MAX_SIZE_BYTES = 2L * 1024 * 1024;
    private static final String PUBLIC_PATH_PREFIX = "/uploads/profile-photos/";

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Value("${medtrack.uploads.profile-photos.dir:./uploads/profile-photos}")
    private String uploadDir;

    public UserResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun fichier fourni");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le fichier dépasse la taille maximale autorisée (2 Mo)");
        }

        String extension = detectImageExtension(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        try {
            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);

            deleteExistingFile(user.getPhotoUrl(), dir);

            String filename = UUID.randomUUID() + "." + extension;
            file.transferTo(dir.resolve(filename).toAbsolutePath());

            user.setPhotoUrl(PUBLIC_PATH_PREFIX + filename);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Échec de l'enregistrement de la photo");
        }

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    public UserResponse delete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        deleteExistingFile(user.getPhotoUrl(), Path.of(uploadDir));
        user.setPhotoUrl(null);

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    private void deleteExistingFile(String currentPhotoUrl, Path dir) {
        if (currentPhotoUrl == null || !currentPhotoUrl.startsWith(PUBLIC_PATH_PREFIX)) {
            return;
        }
        String filename = currentPhotoUrl.substring(PUBLIC_PATH_PREFIX.length());
        try {
            Files.deleteIfExists(dir.resolve(filename));
        } catch (IOException e) {
        }
    }

    private String detectImageExtension(MultipartFile file) {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(12);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier illisible");
        }

        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return "png";
        }
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Format d'image non supporté (JPEG, PNG ou WebP uniquement)");
    }
}
