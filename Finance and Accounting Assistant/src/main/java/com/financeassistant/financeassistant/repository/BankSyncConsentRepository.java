package com.financeassistant.financeassistant.repository;

import com.financeassistant.financeassistant.entity.BankSyncConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankSyncConsentRepository extends JpaRepository<BankSyncConsent, Long> {

    Optional<BankSyncConsent> findTopByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<BankSyncConsent> findByStateToken(String stateToken);
}
