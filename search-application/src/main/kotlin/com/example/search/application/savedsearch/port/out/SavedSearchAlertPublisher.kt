package com.example.search.application.savedsearch.port.out

import com.example.search.domain.savedsearch.NotifyChannel
import com.example.search.domain.savedsearch.SavedSearchAlert

/**
 * SavedSearch 매치 결과를 외부로 발행. 구현체는 채널 type 에 따라 KafkaTemplate / WebClient 분기.
 *
 * 실패 시 throw — 호출자 (스케줄러) 가 lastEvaluatedAt 갱신을 건너뛰고 다음 사이클에 재시도.
 * 다만 webhook 의 4xx 영구 실패는 무한 재시도 방지 위해 구현체가 dead-letter 처리.
 */
interface SavedSearchAlertPublisher {
    fun publish(alert: SavedSearchAlert, channel: NotifyChannel)
}
