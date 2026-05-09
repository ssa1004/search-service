-- 운영자가 관리하는 동의어 사전 (ADR-0017).
--
-- terms 는 줄바꿈 (\n) 으로 join 한 단일 TEXT — H2 / Postgres 양쪽 호환. 도메인 단계에서 term 안의
-- 쉼표 / 화살표 / 백슬래시는 차단되므로 round-trip 안전. 별도 자식 테이블 대안은 reload 시 N+1
-- 비용이 부담되어 단일 컬럼으로 단순화.

CREATE TABLE synonym_groups (
    id         VARCHAR(64)  PRIMARY KEY,
    terms      TEXT         NOT NULL,
    direction  VARCHAR(16)  NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    updated_by VARCHAR(64)  NOT NULL
);

CREATE INDEX ix_synonym_groups_updated_at ON synonym_groups (updated_at);
