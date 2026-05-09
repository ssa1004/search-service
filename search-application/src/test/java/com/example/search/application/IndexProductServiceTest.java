package com.example.search.application;

import com.example.search.application.command.IndexProductCommand;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.port.out.SearchClickRepository;
import com.example.search.application.service.IndexProductService;
import com.example.search.domain.index.IndexDocument;
import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexProductServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock
    IndexWriterPort indexWriter;
    @Mock
    SearchClickRepository clicks;

    @Test
    void 기존_click_누적값을_보존하여_indexing() {
        when(clicks.sumClicksFor(eq(ProductId.of("p-1")), any())).thenReturn(42L);
        IndexProductService service = new IndexProductService(
                indexWriter, clicks, Clock.fixed(NOW, ZoneOffset.UTC));

        Product p = Product.create(ProductId.of("p-1"), "Air Max 1", "Nike", Category.SNEAKERS,
                List.of("260"), Money.won(150_000L), 10, NOW);
        service.index(new IndexProductCommand(p));

        ArgumentCaptor<IndexDocument> captor = ArgumentCaptor.forClass(IndexDocument.class);
        verify(indexWriter).index(captor.capture());
        IndexDocument doc = captor.getValue();
        assertThat(doc.id()).isEqualTo(p.id());
        assertThat(doc.clickCount()).isEqualTo(42L);
        assertThat(doc.version()).isEqualTo(1L);
    }
}
