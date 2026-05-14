package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "skill", schema = "rms",
       uniqueConstraints = @UniqueConstraint(name = "uk_skill_name", columnNames = "skill_name"))
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;
}
