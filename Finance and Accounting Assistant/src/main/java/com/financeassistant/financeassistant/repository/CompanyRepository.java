package com.financeassistant.financeassistant.repository;

import com.financeassistant.financeassistant.entity.Company;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    /**
     * Returns the first company owned by the given user.
     * Used by AuthService after login/register to embed companyId in the JWT.
     */
    Optional<Company> findFirstByOwnerId(Long ownerId);

    /**
     * Checks whether a company with the given id belongs to the given owner.
     */
    boolean existsByIdAndOwnerId(Long id, Long ownerId);
}
