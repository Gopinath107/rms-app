package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "candidate_skill", schema = "rms")
@IdClass(CandidateSkillId.class)
public class CandidateSkill {
    @Id
    @Column(name = "candidate_id")
    private Long candidateId;

    @Id
    @Column(name = "skill_id")
    private Long skillId;
}