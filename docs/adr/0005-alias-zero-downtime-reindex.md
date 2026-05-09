# ADR-0005: alias-based zero-downtime reindex

## 상태
적용

## 배경
ES mapping 은 한 번 만들어진 인덱스에서 변경할 수 없다 (analyzer 추가, 필드 type 변경 등은
모두 신규 인덱스 필요). 운영 중 mapping 변경이 필요하면 어떻게든 무중단으로 새 인덱스로
검색을 옮겨야 한다.

순진하게 application 측에서 인덱스 이름을 바꾸면 배포 사이에 검색이 죽는다. 한쪽에서
새 이름으로 indexing 하고 다른 쪽에서 옛 이름으로 검색하는 race 도 발생.

## 결정
**검색은 항상 alias 로 호출하고, alias 가 가리키는 물리 인덱스를 atomic swap 으로 교체.**

```
검색: GET /products/_search           ← alias = "products"
인덱스: products  → products-v202605   ← 현재
                  → products-v202608   ← reindex 후 swap
```

운영 흐름 (`ReindexAllService`):
1. 현재 alias 가 가리키는 물리 인덱스 확인 (예: `products-v202605`).
2. 새 mapping 으로 새 물리 인덱스 생성 (`products-v202608`).
3. source DB 의 product 를 batch 단위로 읽어 새 인덱스로 bulk indexing.
4. doc count 검증 (source vs new index). 일치하지 않으면 swap 보류 — 운영자가 수동
   검토.
5. ES `_aliases` API 로 atomic swap (remove old + add new 한 번에).
6. 구 인덱스는 deletion delay 후 정리 (rollback 시간 확보 — `dropOld` 옵션 default
   false).

## 장단점
- 검색 측 무중단 — alias swap 은 ES 가 atomic 하게 처리, 동시에 진행 중인 검색은 끝까지
  옛 인덱스에서, 이후 검색은 새 인덱스에서 자동 라우팅.
- mapping / analyzer 변경이 사실상 자유 — 새 인덱스에서 새 매핑으로 빌드.
- doc count 검증으로 잘못된 swap 을 사전 차단.
- 비용은 reindex 동안 디스크 사용량 일시 두 배 (구 + 신 동시 존재) — 그 비용은 ES 노드
  스토리지가 충분하면 부담 적다.

## 다시 검토할 시점
- 인덱스 크기가 수십 GB 를 넘어 reindex 시간이 수 시간이 될 때 — index lifecycle policy
  (주기적 hot/warm/cold tier 이동) 와 결합하거나 ES `_reindex` 의 source slice 분할로
  처리량 늘리기.
- mapping 변경이 backward-compatible 한 경우만 발생할 때는 dynamic mapping 으로 유연
  처리 (비호환 변경만 alias swap 사용).
