package com.healthcare.epa.repository;

import com.healthcare.epa.entity.EpaAdjudicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EpaAdjudicationRepository extends JpaRepository<EpaAdjudicationEntity, String> {
}