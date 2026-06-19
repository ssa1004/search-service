# Screenshots / Demo capture

README / 포트폴리오용 캡처를 **재현 가능하게** 찍기 위한 안내입니다. 이미지는
실제 실행에서만 생성하며 절대 합성하지 않습니다. 아래 명령으로 직접 캡처해
이 디렉토리에 커밋하세요.

캡처 대상 파일명 (README 가 참조하는 이름):

| 파일 | 내용 |
|---|---|
| `swagger-ui.png` | Swagger UI — 검색 / admin endpoint 목록 |
| `demo-run.png` 또는 `demo-run.svg` | `scripts/demo.sh` 한 사이클 콘솔 출력 |
| `actuator-prometheus.png` | `/actuator/prometheus` metric 노출 (선택) |

> Docker 가 없어도 **메모리 모드**로 전부 캡처할 수 있습니다 (ES / Kafka 불필요).

## 1. 앱 부팅 (메모리 모드)

```bash
SEARCH_ENGINE=memory ./gradlew :search-bootstrap:bootRun
# 부팅 완료 로그: "Started SearchApplication" + http://localhost:8080
```

## 2. Swagger UI 캡처 (`swagger-ui.png`)

```bash
# 브라우저로 열기
open http://localhost:8080/swagger-ui.html      # macOS
# xdg-open http://localhost:8080/swagger-ui.html # Linux
```

브라우저에서 endpoint 목록이 펼쳐진 화면을 캡처해 `docs/screenshots/swagger-ui.png` 로
저장합니다. (macOS 영역 캡처: `Cmd+Shift+4`)

헤드리스로 자동 캡처하려면:

```bash
# Chromium 헤드리스 (설치되어 있을 때)
chromium --headless --screenshot=docs/screenshots/swagger-ui.png \
  --window-size=1440,1600 --virtual-time-budget=4000 \
  http://localhost:8080/swagger-ui/index.html
```

## 3. demo.sh 출력 캡처 (`demo-run.png` / `demo-run.svg`)

앱이 떠 있는 상태에서 다른 셸에서:

```bash
# jq 필요 (brew install jq)
./scripts/demo.sh
```

콘솔 출력을 캡처하거나, 터미널 세션을 SVG 로 떨어뜨릴 수 있습니다:

```bash
# asciinema + svg-term (있을 때) — 재생 가능한 SVG
asciinema rec demo.cast -c './scripts/demo.sh'
npx svg-term-cli --in demo.cast --out docs/screenshots/demo-run.svg --window
```

## 4. (선택) Actuator metric 캡처

```bash
curl -s http://localhost:8080/actuator/prometheus | head -40
```

화면 캡처를 `docs/screenshots/actuator-prometheus.png` 로 저장합니다.

## 운영 모드(실제 ES + Kafka) 캡처

Kibana / Kafka UI 스크린샷이 필요하면 인프라를 먼저 띄웁니다:

```bash
docker compose -f infrastructure/docker-compose.yml up -d
# Kibana   : http://localhost:5601
# Kafka UI : http://localhost:8081
```

> 위 명령은 Docker 데몬이 필요합니다. 데몬이 없으면 메모리 모드 캡처(1~4)만으로도
> 핵심 흐름(검색 / 자동완성 / 동의어 / reindex / 분석)을 전부 보여줄 수 있습니다.
