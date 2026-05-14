package com.ris.rms.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidateSkillId implements Serializable {
    private Long candidateId;
    private Long skillId;
}