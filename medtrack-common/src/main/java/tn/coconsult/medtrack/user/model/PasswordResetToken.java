package tn.coconsult.medtrack.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.common.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/** Token de réinitialisation à usage unique. userId en FK brute (même choix que le reste du projet), expire 1h après création. */
@Entity
@Table(name = "password_reset_tokens")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean used = false;

    @PrePersist
    void prePersist() {
        if (this.token == null) {
            this.token = UUID.randomUUID().toString();
        }
        if (this.expiryDate == null) {
            this.expiryDate = LocalDateTime.now().plusHours(1);
        }
    }
}
