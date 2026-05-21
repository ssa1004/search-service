# Contributing

본 저장소의 개발 흐름과 commit 규칙을 정리한 문서입니다.

## 브랜치 전략

GitHub Flow 를 따릅니다. `main` 은 항상 배포 가능한 상태로 유지되며, 모든 작업은 feature
브랜치에서 진행됩니다.

```
main (protected)
  ├── feature/edge-ngram-autocomplete   ← 기능 브랜치
  ├── fix/cdc-offset-rewind
  └── docs/update-readme
```

흐름은 `git checkout -b feature/<짧은-설명>` → 작업 → PR → 코드 리뷰 + CI 통과 → Squash and
merge 입니다. 머지 후 feature 브랜치는 즉시 삭제합니다.

## Commit 메시지

Conventional Commits 형식을 따릅니다.

```
<type>(<scope>): <짧은 설명, 50자 이내>

<상세 설명, 한 줄에 72자 이내>
- 무엇이 / 왜 변경되었는지
- 영향받는 모듈
```

사용하는 type: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`.
scope 에는 모듈명 (`domain`, `application`, `adapter-out`, `adapter-in`, `bootstrap`) 이
들어갑니다.

검색, 자동완성, CDC 인덱싱이 도메인의 핵심이므로 관련 commit 이 자주 발생합니다.

### 예시

```
feat(application): function_score 기반 boost rule

- 인기 상품 (CTR 높음) 가중치 + 신상품 decay
- 검색 결과 ordering 에 반영
- 적용 범위는 SearchProductUseCase 한정
```

```
fix(adapter-out): CDC 컨슈머 offset 이 rebalance 후 0 으로 되감기던 버그

자동 commit 비활성 상태에서 offset commit 시점이 batch 마지막이 아니라 record
마다였던 문제입니다. ack mode 를 BATCH 로 고정하고, 처리 실패 시 retry-listener
가 같은 record 부터 재처리하도록 수정했습니다.
```

## Commit 단위

한 commit 은 한 가지 논리적 변경을 담는 것을 원칙으로 합니다. 새 기능 + 리팩터링 + 버그
수정이 한 commit 에 같이 포함되어 있다면 거의 항상 분리 가능합니다. WIP commit 은 PR 머지
전에 squash 합니다.

## 테스트

PR 전 `./gradlew test` 통과가 필수입니다. 빠른 단위 테스트만 별도로 실행하려면 다음 명령을
사용합니다.

- 도메인: `:search-domain:test`
- 유스케이스 단위: `:search-application:test`
- adapter-out 단위 (mock 기반): `:search-adapter-out:test`
- 통합 시나리오 (Postgres + Kafka + Elasticsearch Testcontainer): `./gradlew integrationTest`
  또는 `:e2e-tests:integrationTest`

`./gradlew test` 는 default 에서 `@Tag("integration")` 을 제외합니다 — Testcontainers ES 가
무거워서 PR CI 의 빠른 피드백을 위해 분리했습니다.

## 코드 스타일

- Kotlin: 공식 코딩 컨벤션 (IntelliJ default) — 들여쓰기 4칸은 `.editorconfig` 로 강제
- 주석 / 문서는 자연스러운 한국어 (영어 직역체 지양)
- 패키지는 헥사고날 — `domain` / `application/{port,service,command}` /
  `adapter/{in,out}` 경계를 넘지 않습니다.
