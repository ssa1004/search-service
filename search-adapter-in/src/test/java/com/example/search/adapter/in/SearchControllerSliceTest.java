package com.example.search.adapter.in;

import com.example.search.adapter.in.web.GlobalExceptionHandler;
import com.example.search.adapter.in.web.SearchController;
import com.example.search.application.command.AutocompleteCommand;
import com.example.search.application.command.SearchProductCommand;
import com.example.search.application.command.SuggestRelatedCommand;
import com.example.search.application.port.in.AutocompleteUseCase;
import com.example.search.application.port.in.RecordSearchClickUseCase;
import com.example.search.application.port.in.SearchProductUseCase;
import com.example.search.application.port.in.SuggestRelatedUseCase;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.query.SearchResult;
import com.example.search.domain.suggest.AutocompleteSuggestion;
import com.example.search.domain.suggest.RelatedSuggestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerSliceTest {

    @Mock
    SearchProductUseCase searchUseCase;
    @Mock
    AutocompleteUseCase autocompleteUseCase;
    @Mock
    SuggestRelatedUseCase relatedUseCase;
    @Mock
    RecordSearchClickUseCase clickUseCase;

    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SearchController controller = new SearchController(
                searchUseCase, autocompleteUseCase, relatedUseCase, clickUseCase);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 검색_정상_요청() throws Exception {
        when(searchUseCase.search(any(SearchProductCommand.class))).thenReturn(
                new SearchResult(1L, 5L,
                        List.of(new SearchResult.Hit(ProductId.of("p-1"), "Air Max", "Nike",
                                "SNEAKERS", 150_000L, 5, "AVAILABLE", 1.5)),
                        List.of()));

        String body = """
                {"keyword":"nike","filters":[],"facets":[],"page":0,"size":20}
                """;
        mvc.perform(post("/api/v1/search/products")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.hits[0].id").value("p-1"));
    }

    @Test
    void 자동완성() throws Exception {
        when(autocompleteUseCase.suggest(any(AutocompleteCommand.class))).thenReturn(List.of(
                new AutocompleteSuggestion("Air Max", ProductId.of("p-1"), 5.0)));

        mvc.perform(get("/api/v1/search/autocomplete").param("q", "Air").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].text").value("Air Max"));
    }

    @Test
    void 관련_검색어() throws Exception {
        when(relatedUseCase.suggest(any(SuggestRelatedCommand.class))).thenReturn(List.of(
                new RelatedSuggestion("nike", 100L, 1)));

        mvc.perform(get("/api/v1/search/related").param("q", "nikr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.related[0].suggestedKeyword").value("nike"));
    }

    @Test
    void 클릭_기록_은_202_Accepted() throws Exception {
        String body = """
                {"productId":"p-1","userId":"u-1","keyword":"nike","rank":3}
                """;
        mvc.perform(post("/api/v1/search/searches/s-1/clicks")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        verify(clickUseCase).record(any());
    }

    @Test
    void 잘못된_filter_op_은_400() throws Exception {
        String body = """
                {"keyword":"nike",
                 "filters":[{"field":"brand","op":"weird","value":"nike"}],
                 "facets":[],"page":0,"size":20}
                """;
        mvc.perform(post("/api/v1/search/products")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
