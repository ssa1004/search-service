<!--
PR 제목은 Conventional Commits 를 따른다 (feat:/fix:/ci:/build:/docs:/test:/chore:).
Squash and merge 시 이 제목이 main 의 커밋 메시지가 된다.
-->

## 무엇을 / 왜 (What & Why)

<!-- 이 변경이 해결하는 문제와 접근을 1~3문장으로. 관련 이슈가 있으면 링크. -->

Closes #

## 변경 유형 (Type)

- [ ] feat — 기능 추가
- [ ] fix — 버그 수정
- [ ] ci / build — 파이프라인 · 빌드 · 배포 표면
- [ ] docs — 문서
- [ ] test — 테스트 보강
- [ ] chore / refactor — 동작 변화 없는 정리

## 변경 내용 (Changes)

<!-- 핵심 변경을 항목으로. 리뷰어가 어디를 봐야 하는지 안내. -->

-

## 체크리스트 (Checklist)

- [ ] `./gradlew check` (단위) + 필요 시 `./gradlew integrationTest` 통과
- [ ] Helm 변경 시 `helm lint` · `helm template | kubeconform` · `helm unittest` 통과
- [ ] Workflow 변경 시 `actionlint` 통과 + 외부 action 은 SHA 핀 유지
- [ ] Dockerfile 변경 시 `hadolint` 통과
- [ ] 공개 API · 운영 동작이 바뀌면 README / ADR 갱신
- [ ] 운영 동작(probe · resource · 보안 컨텍스트 등)을 바꾸지 않았거나, 바꿨다면 본문에 명시

## 검증 (Verification)

<!-- 실제로 돌린 명령과 결과. 로그 일부나 스크린샷 첨부 권장. -->

```
```
