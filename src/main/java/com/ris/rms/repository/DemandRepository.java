package com.ris.rms.repository;

import com.ris.rms.entity.Demand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DemandRepository extends JpaRepository<Demand, Long>, JpaSpecificationExecutor<Demand> {
}