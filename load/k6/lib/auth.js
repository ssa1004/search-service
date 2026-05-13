// mock JWT helper — k6 시나리오에서 Authorization 헤더에 붙일 토큰을 만든다.
//
// search-service 는 현재 REST 컨트롤러 단에서 JWT 를 직접 검증하지 않는다 (README 통합
// 흐름 주석 참조). 통합 환경 / 인접 service 와 묶어 쓸 때만 의미가 있다.
//
//   1) dev 프로필이면 모든 요청을 permitAll() — 토큰이 없어도 200.
//   2) prod 또는 ingress 단 JWT 게이트가 켜진 경우, K6_TOKEN env 로 외부 발급 토큰 주입.
//
// 토큰 라이프사이클 검증이 아닌 검색 endpoint 부하 측정이 목적이라 dev 프로필 + 빈 토큰
// 조합으로 충분하다.

import encoding from 'k6/encoding';

const ENV_TOKEN = __ENV.K6_TOKEN || '';

/**
 * Authorization 헤더 객체를 돌려준다. 토큰이 비어 있으면 빈 객체.
 */
export function authHeader() {
  if (!ENV_TOKEN) return {};
  return { Authorization: `Bearer ${ENV_TOKEN}` };
}

/**
 * 토큰 raw 값을 돌려준다.
 */
export function rawToken() {
  return ENV_TOKEN;
}

/**
 * 운영자 식별 헤더 — admin endpoint (동의어 / reindex / DLT replay) 에서 사용.
 *
 * AdminSynonymController 의 @RequestHeader("X-Operator-Id") 와 짝.
 */
export function operatorHeader(operatorId = 'k6-load') {
  return { 'X-Operator-Id': operatorId };
}

/**
 * 테스트용 unsigned JWT — dev 프로필에서만 의미. 서명은 sha256 가 k6 stdlib 에 없어 빈
 * 값으로 둔다. jwt.io 호환 base64url 인코딩.
 *
 * @param subject {string} — sub claim
 * @param ttlSeconds {number} — exp 까지의 초
 */
export function unsignedJwt(subject = 'k6-load', ttlSeconds = 3600) {
  const header = { alg: 'none', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    sub: subject,
    iat: now,
    exp: now + ttlSeconds,
    scope: 'search:read',
  };
  const part = (o) => base64url(JSON.stringify(o));
  return `${part(header)}.${part(payload)}.`;
}

function base64url(s) {
  return encoding.b64encode(s, 'rawurl');
}
