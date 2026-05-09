-- SavedSearch — 사용자가 저장한 검색 조건 + 5분 단위 평가 알림 (ADR-0016).
-- query_json TEXT 로 보관 — query 구조 변경 (필드 추가) 시 마이그레이션 부담 감소.

CREATE TABLE saved_searches (
    id                 VARCHAR(64)   PRIMARY KEY,
    owner_id           VARCHAR(64)   NOT NULL,
    label              VARCHAR(200)  NOT NULL,
    query_json         TEXT          NOT NULL,
    channel_type       VARCHAR(16)   NOT NULL,
    channel_target     VARCHAR(500)  NOT NULL,
    active             BOOLEAN       NOT NULL,
    created_at         TIMESTAMP     NOT NULL,
    last_evaluated_at  TIMESTAMP
);

-- 사용자별 목록 조회 — "내 알림" 화면.
CREATE INDEX ix_saved_searches_owner ON saved_searches (owner_id);

-- 스케줄러 cursor paging — active=true row 를 id asc 로.
CREATE INDEX ix_saved_searches_active_id ON saved_searches (active, id);
