package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "employee_skill", schema = "rms")
@IdClass(EmployeeSkillId.class)
public class EmployeeSkill {

    @Id
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Id
    @Column(name = "skill_id", nullable = false)
    private Long skillId;
}
