-- P04 网框和版面身份、占框约束、幂等维护履历（dev-20261125-010）
ALTER TABLE engineering_screen_frame
  ADD COLUMN v2_registered BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN revision INT NOT NULL DEFAULT 0,
  ADD CONSTRAINT chk_screen_frame_v2 CHECK
    (v2_registered = 0 OR (v2_registered = 1 AND status IN ('READY','MAINTENANCE','SCRAPPED')));

-- 旧 film_id 保留其原外键；新实体底片通过 version_id + film_copy_id 引用。
ALTER TABLE engineering_screen_plate
  ADD COLUMN v2_registered BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN version_id BIGINT NULL,
  ADD COLUMN film_copy_id BIGINT NULL,
  ADD COLUMN stretch_id BIGINT NULL,
  ADD COLUMN position VARCHAR(100) NULL,
  ADD COLUMN released_by VARCHAR(64) NULL,
  ADD COLUMN released_time DATETIME(3) NULL,
  ADD COLUMN revision INT NOT NULL DEFAULT 0,
  ADD COLUMN occupied_frame_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN v2_registered = 1 AND status IN ('PENDING','FAILED','RELEASED') THEN frame_id END) STORED,
  ADD UNIQUE KEY uk_screen_plate_occupied (occupied_frame_id),
  ADD KEY idx_screen_plate_version_status (version_id,status),
  ADD CONSTRAINT fk_screen_plate_stretch FOREIGN KEY (frame_id,stretch_id)
    REFERENCES print_frame_stretch (frame_id,stretch_id),
  ADD CONSTRAINT fk_screen_plate_copy FOREIGN KEY (version_id,film_copy_id)
    REFERENCES print_film_copy (version_id,film_id),
  ADD CONSTRAINT chk_screen_plate_identity CHECK
    (v2_registered = 0 OR (v2_registered = 1 AND film_id IS NULL AND version_id IS NOT NULL AND
     film_copy_id IS NOT NULL AND stretch_id IS NOT NULL AND position IS NOT NULL AND
     status IN ('PENDING','FAILED','RELEASED','WASHED','SCRAPPED') AND
     (status <> 'RELEASED' OR (released_by IS NOT NULL AND released_time IS NOT NULL))));

ALTER TABLE engineering_resource_maintenance
  ADD COLUMN request_id VARCHAR(64) NULL,
  ADD COLUMN request_hash CHAR(64) NULL,
  ADD COLUMN result_snapshot JSON NULL,
  ADD UNIQUE KEY uk_screen_event_request (request_id);
