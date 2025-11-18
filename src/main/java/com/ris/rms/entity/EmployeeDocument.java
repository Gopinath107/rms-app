package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "employee_document", schema = "rms", indexes = {
		@Index(name = "idx_empdoc_emp", columnList = "employee_id"),
		@Index(name = "idx_empdoc_emp_type", columnList = "employee_id, document_type") })
public class EmployeeDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "document_id")
	private Long documentId;

	@Column(name = "employee_id", nullable = false)
	private Long employeeId;

	@Column(name = "document_name", nullable = false, length = 255)
	private String documentName;

	@Column(name = "file_path", nullable = false, length = 1024)
	private String filePath;

	@Column(name = "uploaded_at", nullable = false, updatable = false, insertable = false)
	private OffsetDateTime uploadedAt;
	@Column(name = "document_type", length = 50)
	private String documentType;

	@Column(name = "mime_type", length = 255)
	private String mimeType;

	@Column(name = "size_bytes")
	private Long sizeBytes;

	@Column(name = "storage_provider", length = 20)
	private String storageProvider;

	@Column(name = "storage_key", length = 1024)
	private String storageKey;

	@Column(name = "checksum_sha256", length = 64)
	private String checksumSha256;

	@Column(name = "is_primary")
	private Boolean isPrimary;

	@Column(name = "version")
	private Integer version;
	
	@Column(name = "resume_share_status", length = 40)
    private String resumeShareStatus;

	@org.hibernate.annotations.ColumnTransformer(write = "?::jsonb")
	@Column(name = "resume_share_meta", columnDefinition = "jsonb")
	private String resumeShareMeta;


}
