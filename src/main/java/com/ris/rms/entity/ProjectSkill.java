package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "project_skill", schema = "rms")
@IdClass(ProjectSkillId.class)
public class ProjectSkill {

    @Id
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Id
    @Column(name = "skill_id", nullable = false)
    private Long skillId;
}
