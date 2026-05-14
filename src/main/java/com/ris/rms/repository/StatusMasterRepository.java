package com.ris.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ris.rms.entity.StatusMaster;

public interface StatusMasterRepository extends JpaRepository<StatusMaster, Long> {

    List<StatusMaster> findByCategoryAndIsActiveTrueOrderByStatusIdAsc(String category);

    List<StatusMaster> findByIsActiveTrueOrderByCategoryAscStatusIdAsc();

    Optional<StatusMaster> findByCategoryAndCode(String category, String code);
}
