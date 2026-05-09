# 한국어 형태소 분석을 위한 nori 플러그인 포함 Elasticsearch 이미지 (ADR-0015).
#
# 본 이미지는 docker-compose 의 build context 로 사용. 운영 K8s 에서는 같은 Dockerfile 을
# CI 가 빌드해 registry 에 push 하고 statefulset 이 image 로 참조.
FROM docker.elastic.co/elasticsearch/elasticsearch:8.15.3

# analysis-nori — 한국어 형태소 분석 (nori_tokenizer / nori_part_of_speech / nori_readingform).
# 설치 시 dependency 자동 다운로드 — build 시점에 한 번만.
RUN bin/elasticsearch-plugin install --batch analysis-nori
