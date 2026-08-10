package tn.coconsult.medtrack.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.common.entity.BaseEntity;

@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Compte désactivé (actif=false) : ne peut plus se connecter ni utiliser un JWT existant.
    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean actif = true;

    @ManyToOne
    @JoinColumn(name = "invited_by")
    private User invitedBy;

    @Column(nullable = true)
    private String photoUrl;

    @Column(nullable = true)
    private String specialite;

    @Column(nullable = true)
    private String numeroOrdre;

    @Column(nullable = true, length = 30)
    private String telephone;
}
