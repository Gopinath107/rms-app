package com.ris.rms.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "password_reset_token", schema = "rms")
public class PasswordResetToken {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "email", nullable = false)
	private String email;

	@Column(name = "otp", nullable = false)
	private String otp;

	@Column(name = "expiry_date", nullable = false)
	private LocalDateTime expiryDate;

	@Column(name = "is_used", nullable = false)
	private Boolean isUsed = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private java.time.OffsetDateTime createdAt;

	@jakarta.persistence.PrePersist
	protected void onCreate() {
		this.createdAt = java.time.OffsetDateTime.now();
	}
}
