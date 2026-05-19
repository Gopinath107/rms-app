-- Migration: Add missing columns to rms.candidate to match employee parity
-- Run this once against your PostgreSQL database.

ALTER TABLE rms.candidate
  ADD COLUMN IF NOT EXISTS middle_name         VARCHAR(100),
  ADD COLUMN IF NOT EXISTS date_of_birth       DATE,
  ADD COLUMN IF NOT EXISTS primary_country_code  VARCHAR(10),
  ADD COLUMN IF NOT EXISTS primary_contact_no    VARCHAR(30),
  ADD COLUMN IF NOT EXISTS secondary_country_code VARCHAR(10),
  ADD COLUMN IF NOT EXISTS secondary_contact_no   VARCHAR(30),
  ADD COLUMN IF NOT EXISTS country_of_citizenship VARCHAR(100),
  ADD COLUMN IF NOT EXISTS document_type         VARCHAR(100),
  ADD COLUMN IF NOT EXISTS document_number       VARCHAR(100),
  ADD COLUMN IF NOT EXISTS security_clearance    VARCHAR(100),
  ADD COLUMN IF NOT EXISTS visa                  VARCHAR(10),
  ADD COLUMN IF NOT EXISTS visa_type             VARCHAR(100),
  ADD COLUMN IF NOT EXISTS country               VARCHAR(100),
  ADD COLUMN IF NOT EXISTS state                 VARCHAR(100),
  ADD COLUMN IF NOT EXISTS city                  VARCHAR(100),
  ADD COLUMN IF NOT EXISTS zip_code              VARCHAR(20),
  ADD COLUMN IF NOT EXISTS street                VARCHAR(500),
  ADD COLUMN IF NOT EXISTS availability_to_join  VARCHAR(100),
  ADD COLUMN IF NOT EXISTS interview_availability VARCHAR(255),
  ADD COLUMN IF NOT EXISTS highest_qualification VARCHAR(255),
  ADD COLUMN IF NOT EXISTS university_name       VARCHAR(255),
  ADD COLUMN IF NOT EXISTS date_of_qualification DATE,
  ADD COLUMN IF NOT EXISTS usa_degree            VARCHAR(100),
  ADD COLUMN IF NOT EXISTS current_job_title     VARCHAR(255),
  ADD COLUMN IF NOT EXISTS most_recent_employer  VARCHAR(255),
  ADD COLUMN IF NOT EXISTS total_experience      INTEGER,
  ADD COLUMN IF NOT EXISTS relocate              VARCHAR(10),
  ADD COLUMN IF NOT EXISTS currency              VARCHAR(20),
  ADD COLUMN IF NOT EXISTS frequency             VARCHAR(30),
  ADD COLUMN IF NOT EXISTS sourcing_rate         NUMERIC(15,2),
  ADD COLUMN IF NOT EXISTS resume_summary        TEXT,
  ADD COLUMN IF NOT EXISTS suggested_keywords    TEXT,
  ADD COLUMN IF NOT EXISTS social_links_json     TEXT,
  ADD COLUMN IF NOT EXISTS current_account_id    BIGINT;

-- Extend phone_number column if it is still 13 chars (legacy)
ALTER TABLE rms.candidate
  ALTER COLUMN phone_number TYPE VARCHAR(50);

COMMENT ON TABLE rms.candidate IS 'External candidate profiles — parity with employee as of 2025-05-19';
