# Security Policy

본 저장소는 개인 학습 / 포트폴리오 목적의 데모 프로젝트입니다. 운영 환경에서 그대로
사용하는 것을 전제로 하지 않습니다.

## 지원 버전

`main` 브랜치만 보안 수정 대상입니다. 태그 / 과거 커밋에는 별도 백포트를 하지 않습니다.

## 취약점 보고

발견한 취약점은 GitHub 의 **Security Advisory → Report a vulnerability** 기능으로 비공개
보고해 주세요. Issue 트래커에 공개 게시하지 말아 주세요.

- 응답 목표: 영업일 기준 7일 이내 1차 회신
- 수정 목표: 심각도에 따라 14 ~ 60일

응답 / 수정 SLA 는 개인 운영 범위 안에서의 best-effort 입니다.

## 의존성 관리

- Spring Boot 의 BOM 으로 transitive dependency 버전을 고정합니다.
- CI 가 워크플로 디스패치로 빌드 + 테스트만 수행합니다 — automated dependency scan
  (Dependabot / Renovate 등) 은 도입 후보입니다.
- CVE 가 공개된 의존성은 patch 버전 우선으로 빠르게 올리고, major / minor 는 호환성
  검증 후 별도 PR 로 분리합니다.

## 운영 시 주의

- `infrastructure/docker-compose.yml` 의 ES / Postgres / Kafka 는 로컬 통합 환경 전용으로
  비밀번호가 약합니다. 외부 노출 환경에서 그대로 쓰지 마세요.
- 운영 배포 시 Helm chart 의 `values.yaml` 기본값 (replica / HPA / NetworkPolicy off) 을
  검토하고 환경별 override (`values-prod.yaml`) 를 적용하세요.
