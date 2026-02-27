package com.financeassistant.financeassistant.repository;

import com.financeassistant.financeassistant.entity.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {
    List<Anomaly> findByCompanyIdOrderByDetectedAtDesc(Long companyId);
}