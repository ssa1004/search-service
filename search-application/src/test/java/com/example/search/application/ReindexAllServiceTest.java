package com.example.search.application;

import com.example.search.application.command.ReindexAllCommand;
import com.example.search.application.command.ReindexResult;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.port.out.ProductSourceRepository;
import com.example.search.application.port.out.SearchClickRepository;
import com.example.search.application.port.out.SearchIndexProperties;
import com.example.search.application.service.ReindexAllService;
import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReindexAllServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock
    ProductSourceRepository products;
    @Mock
    SearchClickRepository clicks;
    @Mock
    IndexWriterPort indexWriter;
    @Mock
    SearchIndexProperties properties;

    @Test
    void doc_count_가_일치하면_alias_swap_수행() {
        when(properties.alias()).thenReturn("products");
        when(properties.reindexBatchSize()).thenReturn(50);
        when(indexWriter.currentPhysicalName()).thenReturn("products-v1");
        when(products.countAll()).thenReturn(2L);
        when(products.findAll(0, 50)).thenReturn(List.of(sampleProduct("p-1"), sampleProduct("p-2")));
        when(indexWriter.countDocuments("products-v202605")).thenReturn(2L);
        when(clicks.sumClicksFor(any(), any())).thenReturn(0L);

        ReindexAllService service = new ReindexAllService(
                products, clicks, indexWriter, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        ReindexResult result = service.reindex(new ReindexAllCommand("v202605", false));

        assertThat(result.swapped()).isTrue();
        assertThat(result.newPhysicalName()).isEqualTo("products-v202605");
        verify(indexWriter).createIndex("products-v202605");
        verify(indexWriter).bulkIndex(any());
        verify(indexWriter).swapAlias("products-v1", "products-v202605");
        verify(indexWriter, never()).deleteIndex(anyString());
    }

    @Test
    void doc_count_불일치_시_alias_swap_보류() {
        when(properties.alias()).thenReturn("products");
        when(properties.reindexBatchSize()).thenReturn(50);
        when(indexWriter.currentPhysicalName()).thenReturn("products-v1");
        when(products.countAll()).thenReturn(2L);
        when(products.findAll(0, 50)).thenReturn(List.of(sampleProduct("p-1"), sampleProduct("p-2")));
        // target 에 1건만 들어감 (의도된 시나리오 — 한 건이 indexing 누락된 상황)
        when(indexWriter.countDocuments("products-v202605")).thenReturn(1L);
        when(clicks.sumClicksFor(any(), any())).thenReturn(0L);

        ReindexAllService service = new ReindexAllService(
                products, clicks, indexWriter, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        ReindexResult result = service.reindex(new ReindexAllCommand("v202605", true));

        assertThat(result.swapped()).isFalse();
        assertThat(result.countsMatch()).isFalse();
        verify(indexWriter, never()).swapAlias(anyString(), anyString());
        verify(indexWriter, never()).deleteIndex(anyString());
    }

    @Test
    void dropOld_true_그리고_count_일치_시_구_인덱스_삭제() {
        when(properties.alias()).thenReturn("products");
        when(properties.reindexBatchSize()).thenReturn(50);
        when(indexWriter.currentPhysicalName()).thenReturn("products-v1");
        when(products.countAll()).thenReturn(1L);
        when(products.findAll(0, 50)).thenReturn(List.of(sampleProduct("p-1")));
        when(indexWriter.countDocuments("products-v2")).thenReturn(1L);
        when(clicks.sumClicksFor(any(), any())).thenReturn(0L);

        ReindexAllService service = new ReindexAllService(
                products, clicks, indexWriter, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        service.reindex(new ReindexAllCommand("v2", true));

        verify(indexWriter).swapAlias("products-v1", "products-v2");
        verify(indexWriter).deleteIndex("products-v1");
    }

    private Product sampleProduct(String id) {
        return Product.create(ProductId.of(id), "name-" + id, "Nike", Category.SNEAKERS,
                List.of("260"), Money.won(150_000L), 10, NOW);
    }
}
