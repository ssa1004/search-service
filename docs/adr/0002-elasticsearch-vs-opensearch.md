# ADR-0002: Elasticsearch vs OpenSearch — 라이선스 + 운영 표준

## 상태
적용

## 배경
검색 엔진은 Elasticsearch (Elastic) 와 OpenSearch (AWS fork) 양대 진영이다. 2021년 Elastic
의 라이선스 변경 (Apache 2.0 → SSPL/Elastic License 2.0) 이후 두 진영의 운영 도구 / Java
client / 매니지드 서비스가 갈라졌다. 새 프로젝트가 어느 쪽을 기본 표준으로 삼을지는
운영 비용과 클라이언트 호환성에 직결된다.

## 결정
**Elasticsearch 8.15 + 공식 Java Client (`co.elastic.clients:elasticsearch-java`) 를 채택.**

선택 근거:
- Elasticsearch 공식 Java Client 가 8.x 부터 typed builder 패턴 + lambda DSL 로 정리됨.
  function_score / aggregation 같은 복잡한 query 표현이 OpenSearch Java Client (3.x) 보다
  명시적이다.
- 운영 매니지드 서비스 (Elastic Cloud) 가 멀티 AZ + index lifecycle management 가 안정적.
  AWS 의 OpenSearch Service 는 ILM 정책 적용 시 일부 ES 표준 문법이 미지원.
- 라이선스 (Elastic License 2.0) 는 SaaS 재판매가 아니면 운영 / 사내 사용에 제약이 없다.
  본 프로젝트는 검색 결과를 사용자에게 제공할 뿐 ES 자체를 호스팅 판매하지 않으므로
  라이선스 이슈가 없다.

port (`SearchEnginePort`, `IndexWriterPort`) 가 분리되어 있어 운영 정책상 OpenSearch 로
교체가 필요하면 adapter 만 추가하면 된다.

## 장단점
- 8.x typed Java Client 의 표현력이 좋다 — DSL 빌더가 record / sealed interface 와 잘 맞음.
- ES 라이선스 변경의 정치적 부담이 있다 — 그 부담은 SaaS 재판매가 아니면 실무 영향 없음.
- 매니지드 비용은 둘 다 비슷 (월 수십~백만 원, 노드/스토리지 단위).
- OpenSearch 가 강한 곳은 AWS 통합 (IAM 인증, AWS sigv4) — 여기서는 일반 BasicAuth +
  네트워크 분리로 충분.

## 다시 검토할 시점
- AWS-only 환경으로 인프라가 굳혔고 OpenSearch Service 의 IAM 통합이 운영 단순화에
  결정적일 때.
- ES 의 라이선스가 또 한 번 변해 SaaS 재판매에 영향을 줄 때 (현재는 무관).
