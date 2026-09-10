package io.github.mohammedfaraj46.backend.repository;

import io.github.mohammedfaraj46.backend.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
