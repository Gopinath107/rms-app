package com.ris.rms.repository;

import com.ris.rms.entity.ResourceRequestSkill;
import com.ris.rms.entity.ResourceRequestSkillId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceRequestSkillRepository extends JpaRepository<ResourceRequestSkill, ResourceRequestSkillId> {
    List<ResourceRequestSkill> findAllByRequestId(Long requestId);
    void deleteAllByRequestId(Long requestId);
}
