package com.financeassistant.financeassistant.repository;

import com.financeassistant.financeassistant.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByCompany_Id(Long companyId);

    @Query("SELECT c FROM Category c " +
            "WHERE c.company.id = :companyId OR c.company IS NULL " +
            "ORDER BY c.type ASC, c.isDefault DESC, c.name ASC")
    List<Category> findAvailableForCompany(@Param("companyId") Long companyId);

    Optional<Category> findByCompany_IdAndNameIgnoreCase(Long companyId, String name);

    Optional<Category> findByCompanyIsNullAndNameIgnoreCase(String name);
}
