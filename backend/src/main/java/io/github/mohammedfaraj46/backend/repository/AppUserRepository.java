package io.github.mohammedfaraj46.backend.repository;

import io.github.mohammedfaraj46.backend.domain.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Emails are stored normalised to lower case, so pass a normalised value. */
    boolean existsByEmail(String email);

    Optional<AppUser> findByEmail(String email);
}
