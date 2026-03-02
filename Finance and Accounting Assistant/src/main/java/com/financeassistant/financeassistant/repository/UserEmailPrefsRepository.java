package com.financeassistant.financeassistant.repository;
// PATH: UserEmailPrefsRepository.java
import com.financeassistant.financeassistant.entity.UserEmailPrefs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface UserEmailPrefsRepository extends JpaRepository<UserEmailPrefs, Long> {
    Optional<UserEmailPrefs> findByUserId(Long userId);
}
