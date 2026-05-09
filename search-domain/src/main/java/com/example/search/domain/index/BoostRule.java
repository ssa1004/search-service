package com.example.search.domain.index;

import java.time.Duration;
import java.util.Objects;

/**
 * relevance boost 규칙. ES 의 function_score query 에 매핑된다.
 *
 * <p>두 가지 시그널 조합:</p>
 * <ul>
 *   <li>{@code popularityWeight} — clickCount 의 log 함수에 곱해지는 가중치. clickCount 가 폭발적으로
 *       커도 점수는 log 곡선이라 안정적.</li>
 *   <li>{@code freshnessHalfLife} — 출시일 decay 의 반감기. 출시 직후 1.0, 반감기 만큼 지나면 0.5,
 *       두 배 지나면 0.25 로 감쇠 (gauss decay).</li>
 * </ul>
 *
 * <p>도메인이 weight 의 sane range 를 강제 — 누군가 weight=10000 을 넣어 점수를 망가뜨리는 사고
 * 방지.</p>
 */
public record BoostRule(double popularityWeight, Duration freshnessHalfLife) {

    public static final double MAX_POPULARITY_WEIGHT = 10.0;
    public static final Duration MIN_HALF_LIFE = Duration.ofDays(1);
    public static final Duration MAX_HALF_LIFE = Duration.ofDays(365);

    public BoostRule {
        Objects.requireNonNull(freshnessHalfLife, "freshnessHalfLife");
        if (popularityWeight < 0.0 || popularityWeight > MAX_POPULARITY_WEIGHT) {
            throw new IllegalArgumentException(
                    "popularityWeight 0.." + MAX_POPULARITY_WEIGHT + ": " + popularityWeight);
        }
        if (freshnessHalfLife.compareTo(MIN_HALF_LIFE) < 0
                || freshnessHalfLife.compareTo(MAX_HALF_LIFE) > 0) {
            throw new IllegalArgumentException(
                    "freshnessHalfLife " + MIN_HALF_LIFE + ".." + MAX_HALF_LIFE
                            + ": " + freshnessHalfLife);
        }
    }

    /**
     * 운영 default — 인기도 1.5 가중, 신상품 반감기 30일.
     */
    public static BoostRule defaults() {
        return new BoostRule(1.5, Duration.ofDays(30));
    }

    /**
     * 시그널 비활성. 검증/디버깅 시 boost 영향 제거하려고 사용.
     */
    public static BoostRule disabled() {
        return new BoostRule(0.0, MAX_HALF_LIFE);
    }

    public boolean isDisabled() {
        return popularityWeight == 0.0;
    }
}
