package com.example.search.application;

import com.example.search.application.command.IndexProductCommand;
import com.example.search.application.port.in.IndexProductUseCase;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.service.HandleProductChangeService;
import com.example.search.domain.event.ProductChangeEvent;
import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HandleProductChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock
    IndexProductUseCase indexer;
    @Mock
    IndexWriterPort indexWriter;

    @InjectMocks
    HandleProductChangeService service;

    @Test
    void INSERT_는_indexer_위임() {
        Product p = Product.create(ProductId.of("p-1"), "Air Max", "Nike", Category.SNEAKERS,
                List.of("260"), Money.won(150_000L), 10, NOW);
        service.handle(ProductChangeEvent.insert(p, NOW));

        ArgumentCaptor<IndexProductCommand> captor = ArgumentCaptor.forClass(IndexProductCommand.class);
        verify(indexer).index(captor.capture());
        assertThat(captor.getValue().product()).isEqualTo(p);
        verifyNoInteractions(indexWriter);
    }

    @Test
    void DELETE_는_ES_문서_삭제() {
        ProductId id = ProductId.of("p-2");
        service.handle(ProductChangeEvent.delete(id, 5L, NOW));
        verify(indexWriter).delete(id);
        verifyNoInteractions(indexer);
    }
}
