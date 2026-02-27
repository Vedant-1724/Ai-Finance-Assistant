package com.financeassistant.financeassistant.repository;

import com.financeassistant.financeassistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Used by AuthService to load a user by email during login */
    Optional<User> findByEmail(String email);

    /** Used by AuthService to check for duplicate email during register */
    boolean existsByEmail(String email);
}
