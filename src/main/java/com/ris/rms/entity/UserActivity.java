package com.ris.rms.entity;

import java.time.OffsetDateTime;

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
@Table(name = "user_activity", schema = "rms")
public class UserActivity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "activity_id")
	private Long activityId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "user_name")
	private String userName;

	@Column(name = "event_type", nullable = false, length = 50)
	private String eventType;

	@Column(name = "module_name", length = 255)
	private String moduleName;

	@Column(name = "screen_name", length = 255)
	private String screenName;

	@Column(name = "ip_address", length = 100)
	private String ipAddress;

	@Column(name = "user_agent", length = 1024)
	private String userAgent;

	@Column(name = "session_id", nullable = false, length = 255)
	private String sessionId;

	@Column(name = "event_time", nullable = false)
	private OffsetDateTime eventTime;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;
}
