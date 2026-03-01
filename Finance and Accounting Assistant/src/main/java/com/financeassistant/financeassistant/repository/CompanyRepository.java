package com.financeassistant.financeassistant.repository;

import com.financeassistant.financeassistant.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    /**
     * Returns the first company owned by the given user.
     * Used by AuthService after login/register to embed companyId in the JWT.
     */
    Optional<Company> findFirstByOwnerId(Long ownerId);

    /**
     * Checks whether a company with the given id belongs to the given owner.
     * Used by CompanySecurityService for @PreAuthorize checks.
     */
    boolean existsByIdAndOwnerId(Long id, Long ownerId);
}