package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "resource_request_skill", schema = "rms")
@IdClass(ResourceRequestSkillId.class)
public class ResourceRequestSkill {

    @Id
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Id
    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "skill_type", length = 50) 
    private String skillType;
}
