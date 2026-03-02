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
    boolean existsByCompanyIdAndUserId(Long companyId, Long userId);
}
