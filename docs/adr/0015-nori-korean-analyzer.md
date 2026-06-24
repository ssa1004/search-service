# ADR-0015: nori 한국어 형태소 analyzer

## 상태
적용

## 배경
대상 도메인의 검색어가 대부분 한국어 ("에어 조던 1 시카고", "덩크로우 파나마") 또는 한영
혼합이다. 기존 `ko_standard` 는 이름만 한국어였고 실체는 `standard` tokenizer + lowercase
+ asciifolding — 영어 기준 분리.

문제 사례:
- "에어 조던 1 시카고" 검색 시 standard tokenizer 가 공백으로만 자르므로 "조던1" 처럼 띄어
  쓰지 않은 변형은 매칭 실패.
- "나이키의" 처럼 조사 붙은 검색어는 색인된 "나이키" 와 매칭 실패 — recall 떨어짐.
- 브랜드 / 모델명 ("덩크로우", "이지부스트") 이 분해돼 "덩크" / "로우" 로 잘려 의미 흐림.

## 결정
**Elasticsearch `analysis-nori` 플러그인 + `nori_tokenizer` 기반 custom analyzer.**

### Analyzer 구성
```
ko_standard:
  tokenizer:
    nori_user_dict (nori_tokenizer + decompound_mode=mixed + user_dictionary)
  filter:
    - lowercase
    - asciifolding
    - nori_part_of_speech     // 조사 / 어미 제거
    - nori_readingform        // 한자 → 한글 변환
```

- `decompound_mode=mixed` — 복합어를 분해 후 원형까지 같이 색인 ("덩크로우" → "덩크로우"+"덩크"+"로우").
- `nori_part_of_speech` 의 default stoptag (조사 / 어미 등) 로 의미 없는 토큰 제거.
- `nori_readingform` — 한자 표기가 들어와도 한글로 통일 (현재 도메인엔 거의 없지만 defensive).

### user_dictionary
브랜드 / 모델명을 단일 토큰으로 보존 — 분해되면 의미 흐릿:
- 조던1, 에어조던1, 덩크로우, 덩크하이, 이지부스트
- 인덱스 settings 에 inline (`user_dictionary_rules`) — 외부 파일 dependency 없음.
- 운영에서 늘면 별도 파일 + S3 sync 패턴으로 외부화.

### 인프라 변경
- `docker/elasticsearch/Dockerfile` — `elasticsearch-plugin install --batch analysis-nori`.
- `infrastructure/docker-compose.yml` — `image:` → `build:` 로 전환해 위 Dockerfile 사용.
- e2e Testcontainers 가 같은 Dockerfile 빌드 후 실행 — IT 가 운영 mapping 그대로 검증.

### 검증
- 단위 테스트 `NoriAnalyzerIT` (Tag=integration) — `_analyze` API 로
  - "에어 조던1 시카고" → tokens 에 "조던1" / "에어" / "시카고" 포함.
  - "나이키의 신상품" → "의" 제거, "나이키" 보존.

## 장단점
- 장점: 한국어 검색 recall 즉시 개선 — 조사 무관 매칭, 띄어쓰기 변형 흡수.
- 장점: user_dictionary 로 도메인 어휘 (브랜드 / 모델) 안전 보존.
- 장점: 운영 mapping 과 IT mapping 일치 — IT 가 nori 누락 회귀 잡음.
- 단점: ES 노드에 플러그인 설치 필수 — Elastic Cloud 에서는 별도 설정 (가능은 함).
- 단점: nori 사전이 일반 단어 위주라 도메인 특수 어휘는 user_dictionary 로 계속 보강 필요.
- 단점: Dockerfile 빌드가 IT 시작 시간 (cold) 늘림 — cache 활용으로 보통 한 번만.

## 용어 풀이 (쉽게)

- **nori 형태소 분석** — 한국어 문장을 '의미 단위'로 쪼개는 한국어 전용 분석기. '나이키의'에서 조사 '의'를 떼어 색인된 '나이키'와 매칭되게 한다.
- **tokenizer(토크나이저)** — 문장을 검색용 토막(토큰)으로 자르는 칼. 영어식은 띄어쓰기로만 잘라 '조던1' 같은 한국어 변형을 못 잡는다.
- **decompound(복합어 분해)** — '덩크로우'를 '덩크'+'로우'로 쪼개 부분 검색도 되게 하는 것. mixed 모드는 쪼갠 조각과 원형('덩크로우')을 함께 남긴다.
- **user_dictionary(사용자 사전)** — '조던1','이지부스트' 같은 브랜드·모델명이 엉뚱하게 쪼개지지 않게 "이건 한 덩어리"라고 미리 등록해 두는 사전.
- **조사 / 어미 제거(part_of_speech)** — '신상품을'에서 '을' 같은 의미 없는 토막을 떼어 핵심 단어만 색인하는 것.
- **recall vs precision(재현율·정밀도)** — recall은 '관련된 걸 빠짐없이 찾았나'(너무 적게 안 빠뜨림), precision은 '엉뚱한 게 안 섞였나'(너무 많이 안 끌어옴). 둘은 보통 맞바꿈 관계다.

## 다시 검토할 시점
- user_dictionary 가 코드 inline 으로 관리 어려워지면 (예: 100+ 항목) 외부 파일 + 운영자가
  관리하는 워크플로 도입.
- 한자 / 일본어 입력이 의미 있어지면 ICU normalizer 추가 검토.
- recall 우선 vs precision 균형이 깨지면 (너무 많이 매칭) `decompound_mode=discard` 고려.
