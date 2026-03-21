package com.financeassistant.financeassistant.repository;
// PATH: CompanyMemberRepository.java
import com.financeassistant.financeassistant.entity.CompanyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Long> {
    List<CompanyMember> findByCompanyIdOrderByCreatedAtAsc(Long companyId);
    Optional<CompanyMember> findByInviteToken(String token);
    Optional<CompanyMember> findByCompanyIdAndUserId(Long companyId, Long userId);
    Optional<CompanyMember> findByCompanyIdAndUserIdAndAcceptedAtIsNotNull(Long companyId, Long userId);
    Optional<CompanyMember> findFirstByUserIdAndAcceptedAtIsNotNullOrderByCreatedAtAsc(Long userId);
    boolean existsByCompanyIdAndUserId(Long companyId, Long userId);
    boolean existsByCompanyIdAndUserIdAndAcceptedAtIsNotNull(Long companyId, Long userId);
}
