package tn.coconsult.medtrack.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    Optional<User> findByIdAndRole(Long id, Role role);
}
