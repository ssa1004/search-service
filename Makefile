# search-service — 자주 쓰는 명령 단일 진입점
#
#   make up        인프라(Postgres/Elasticsearch+nori/Kibana/Kafka/Kafka-UI) 기동
#   make ps        컨테이너 상태
#   make logs      인프라 로그 follow
#   make run       앱 메모리 모드 부팅 (ES/Kafka 없이 — 전체 use case 동작)
#   make run-es    앱 운영 모드 부팅 (실제 ES + Kafka)
#   make demo      검색/자동완성/관련/클릭/동의어/reindex/분석 한 cycle (앱이 떠 있어야 함)
#   make down      인프라 정지 (볼륨 유지)
#   make clean     인프라 정지 + 볼륨 삭제 (옛 데이터 제거)
#   make build     전체 gradle 빌드 (테스트 제외)
#   make test      단위 테스트 (integration 제외)
#   make it        통합 테스트 (Testcontainers — Postgres + Kafka + Elasticsearch)
#
# 앱은 호스트에서 ./gradlew bootRun 으로 띄운다 — Kafka 는 localhost:9092 로 붙는다
# (infrastructure/docker-compose.yml 의 EXTERNAL listener). 자세한 건 README "Quick Start".

COMPOSE := docker compose -f infrastructure/docker-compose.yml
GRADLE  := ./gradlew

.DEFAULT_GOAL := help
.PHONY: help up ps logs run run-es demo down clean build test it urls

help: ## 이 도움말
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

up: ## 인프라 기동 (Postgres/Elasticsearch+nori/Kibana/Kafka/Kafka-UI)
	$(COMPOSE) up -d --build
	@echo "→ Elasticsearch :9200 · Kibana :5601 · Kafka-UI http://localhost:8081"

ps: ## 컨테이너 상태
	$(COMPOSE) ps

logs: ## 인프라 로그 follow
	$(COMPOSE) logs -f --tail=100

run: ## 앱 메모리 모드 부팅 (ES/Kafka 없이 — :8080)
	SEARCH_ENGINE=memory $(GRADLE) :search-bootstrap:bootRun

run-es: ## 앱 운영 모드 부팅 (실제 ES + Kafka — 먼저 make up)
	SEARCH_ENGINE=elasticsearch SEARCH_KAFKA_ENABLED=true \
	ELASTICSEARCH_HOST=localhost:9200 KAFKA_BOOTSTRAP=localhost:9092 \
	$(GRADLE) :search-bootstrap:bootRun

demo: ## 데모 시나리오 (앱이 떠 있어야 함)
	./scripts/demo.sh

down: ## 인프라 정지 (볼륨 유지)
	$(COMPOSE) down

clean: ## 인프라 정지 + 볼륨 삭제 (다음 기동 시 깨끗한 상태)
	$(COMPOSE) down -v

build: ## 전체 gradle 빌드 (테스트 제외)
	$(GRADLE) build -x test

test: ## 단위 테스트 (integration 제외)
	$(GRADLE) test

it: ## 통합 테스트 (Testcontainers — Postgres + Kafka + Elasticsearch)
	$(GRADLE) integrationTest

urls: ## 주요 UI / 엔드포인트
	@echo "Swagger UI   http://localhost:8080/swagger-ui.html"
	@echo "Elasticsearch http://localhost:9200"
	@echo "Kibana       http://localhost:5601"
	@echo "Kafka-UI     http://localhost:8081"
	@echo "search-service  :8080"
