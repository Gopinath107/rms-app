package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "candidate_document", schema = "rms")
public class CandidateDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "uploaded_at",  updatable = false)
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

    private Integer version;

    @Column(name = "resume_share_status", length = 40)
    private String resumeShareStatus;

    @org.hibernate.annotations.ColumnTransformer(write = "?::jsonb")
    @Column(name = "resume_share_meta", columnDefinition = "jsonb")
    private String resumeShareMeta;
    
    @PrePersist
    public void prePersist() {
        if (this.uploadedAt == null) {
            this.uploadedAt = OffsetDateTime.now();
        }
    }
}