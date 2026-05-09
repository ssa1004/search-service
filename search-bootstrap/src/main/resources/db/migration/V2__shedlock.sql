-- ShedLock — @Scheduled 중복 방지 lock 보관 (ADR-0014).
-- Postgres / H2 호환 표준 SQL. ShedLock JDBC provider 가 SELECT FOR UPDATE 로 lease 획득.

CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP    NOT NULL,
    locked_at   TIMESTAMP    NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
