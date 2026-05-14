package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "status_master", schema = "rms", uniqueConstraints = @UniqueConstraint(columnNames = { "category",
		"code" }))
public class StatusMaster {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "status_id")
	private Long statusId;

	@Column(name = "category", nullable = false, length = 40)
	private String category;

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "label", nullable = false, length = 80)
	private String label;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;
}
