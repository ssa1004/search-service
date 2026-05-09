package com.example.search.domain.savedsearch;

import java.util.Objects;

/**
 * 알림 전달 경로 — webhook URL / Kafka topic 식별자.
 *
 * <p>도메인은 전송 자체를 알지 못한다 — 이 record 는 "어떤 type 의 어떤 target 으로 보낼지" 만 표현
 * 하고, 실제 발송은 outbound port 의 구현체 책임. 새 타입 (email, slack 등) 이 늘어나면 enum 에
 * 항목만 추가하면 된다.</p>
 */
public record NotifyChannel(Type type, String target) {

    public NotifyChannel {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        if (target.isBlank()) {
            throw new IllegalArgumentException("NotifyChannel target 빈 값 불가");
        }
        if (type == Type.WEBHOOK && !(target.startsWith("http://") || target.startsWith("https://"))) {
            throw new IllegalArgumentException("WEBHOOK target 은 http(s):// 로 시작해야 함: " + target);
        }
    }

    public enum Type {
        /** Kafka topic 발행 — 다른 서비스가 consume 해 사용자별 push / email 로 변환. */
        KAFKA,
        /** 외부 HTTP webhook — 사용자가 직접 등록한 URL 로 POST. */
        WEBHOOK
    }

    public static NotifyChannel kafka(String topic) {
        return new NotifyChannel(Type.KAFKA, topic);
    }

    public static NotifyChannel webhook(String url) {
        return new NotifyChannel(Type.WEBHOOK, url);
    }
}
