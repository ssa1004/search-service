package com.example.search.domain.savedsearch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotifyChannelTest {

    @Test
    void kafka_factory_는_KAFKA_타입() {
        NotifyChannel ch = NotifyChannel.kafka("search.alert.fired");
        assertThat(ch.type()).isEqualTo(NotifyChannel.Type.KAFKA);
        assertThat(ch.target()).isEqualTo("search.alert.fired");
    }

    @Test
    void webhook_은_http_또는_https_만_허용() {
        NotifyChannel ok = NotifyChannel.webhook("https://example.test/hook");
        assertThat(ok.type()).isEqualTo(NotifyChannel.Type.WEBHOOK);

        assertThatThrownBy(() -> NotifyChannel.webhook("ftp://example.test/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http(s)");
    }

    @Test
    void target_빈_값_예외() {
        assertThatThrownBy(() -> NotifyChannel.kafka(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target");
    }
}
