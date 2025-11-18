package com.ris.rms.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ResourceRequestSkillId implements Serializable {
    private Long requestId;
    private Long skillId;
}
