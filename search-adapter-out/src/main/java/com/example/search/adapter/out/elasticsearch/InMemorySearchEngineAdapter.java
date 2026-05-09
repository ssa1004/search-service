package com.example.search.adapter.out.elasticsearch;

import com.example.search.application.command.SearchProductCommand;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.port.out.SearchEnginePort;
import com.example.search.domain.facet.FacetResult;
import com.example.search.domain.facet.FacetSpec;
import com.example.search.domain.index.IndexDocument;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.query.FilterCriterion;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.query.SearchResult;
import com.example.search.domain.suggest.AutocompleteSuggestion;
import com.example.search.domain.suggest.RelatedSuggestion;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 메모리 기반 SearchEnginePort + IndexWriterPort 구현 — 로컬 dev / 단위 테스트용.
 *
 * <p>Elasticsearch 가 없는 환경에서도 REST + use case 가 동작하도록 단순한 contains / startsWith /
 * Levenshtein 매칭만 한다. 운영 분석기 (edge_ngram, fuzziness) 는 흉내만 — 실제 정확도는 ES 어댑터에
 * 의존.</p>
 *
 * <p>이 클래스는 thread-safe — 동시 indexing / search 가 안전하게 동작.</p>
 */
@Slf4j
public class InMemorySearchEngineAdapter implements SearchEnginePort, IndexWriterPort {

    private final Map<String, IndexDocument> docs = new ConcurrentHashMap<>();
    private final AtomicReference<String> currentPhysical = new AtomicReference<>();

    @Override
    public SearchResult search(SearchProductCommand command) {
        SearchQuery sq = command.query();
        long start = System.nanoTime();

        List<IndexDocument> filtered = docs.values().stream()
                .filter(d -> matchKeyword(d, sq.keyword()))
                .filter(d -> matchFilters(d, sq.filters()))
                .toList();

        // boost rule 의 단순 가산 — popularityWeight * log(clickCount + 2). 신상품 decay 는 생략
        // (메모리 구현은 스모크 테스트용이라 정확한 점수가 중요하지 않음).
        var rule = command.boostRule();
        List<SearchResult.Hit> all = filtered.stream()
                .map(d -> new SearchResult.Hit(
                        d.id(), d.name(), d.brand(), d.category(),
                        d.priceWon(), d.stockQuantity(), d.status(),
                        rule.popularityWeight() * Math.log(d.clickCount() + 2)))
                .sorted(Comparator.comparingDouble(SearchResult.Hit::score).reversed())
                .toList();

        int from = sq.page().from();
        int to = Math.min(from + sq.page().size(), all.size());
        List<SearchResult.Hit> paged = from >= all.size() ? List.of() : all.subList(from, to);

        List<FacetResult> facets = computeFacets(filtered, command.facetSpecs());
        long took = (System.nanoTime() - start) / 1_000_000;

        return new SearchResult(all.size(), Math.max(took, 1L), paged, facets);
    }

    @Override
    public List<AutocompleteSuggestion> autocomplete(String prefix, int limit) {
        String p = prefix.toLowerCase();
        return docs.values().stream()
                .filter(d -> d.name().toLowerCase().startsWith(p))
                .sorted(Comparator.comparingLong(IndexDocument::clickCount).reversed())
                .limit(limit)
                .map(d -> new AutocompleteSuggestion(d.name(), d.id(), (double) d.clickCount()))
                .toList();
    }

    @Override
    public List<RelatedSuggestion> findRelatedKeywords(String keyword, int limit, int maxDistance) {
        String k = keyword.toLowerCase();
        return docs.values().stream()
                .map(d -> new RelatedSuggestion(d.name(), d.clickCount(),
                        LevenshteinDistance.compute(k, d.name().toLowerCase())))
                .filter(r -> !r.suggestedKeyword().equalsIgnoreCase(keyword))
                .filter(r -> r.distance() <= maxDistance)
                .sorted(Comparator.comparingLong(RelatedSuggestion::popularity).reversed())
                .limit(limit)
                .toList();
    }

    // ── IndexWriterPort ─────────────────────────────────────────────

    @Override
    public void index(IndexDocument document) {
        docs.merge(document.id().value(), document, (existing, incoming) ->
                // external version — 들어오는 게 더 크거나 같으면 적용, 아니면 무시 (멱등).
                incoming.version() >= existing.version() ? incoming : existing);
    }

    @Override
    public void bulkIndex(List<IndexDocument> documents) {
        documents.forEach(this::index);
    }

    @Override
    public void delete(ProductId id) {
        docs.remove(id.value());
    }

    @Override
    public void incrementClickCount(ProductId id) {
        docs.computeIfPresent(id.value(), (k, doc) -> new IndexDocument(
                doc.id(), doc.name(), doc.brand(), doc.category(), doc.sizes(),
                doc.priceWon(), doc.stockQuantity(), doc.status(),
                doc.clickCount() + 1L, doc.version(), doc.releasedAt(), doc.updatedAt()));
    }

    @Override
    public void createIndex(String physicalName) {
        currentPhysical.compareAndSet(null, physicalName);
    }

    @Override
    public String currentPhysicalName() {
        return currentPhysical.get();
    }

    @Override
    public long countDocuments(String physicalName) {
        // 메모리 구현은 단일 인덱스만 시뮬 — physicalName 무관.
        return docs.size();
    }

    @Override
    public long reindex(String sourcePhysicalName, String targetPhysicalName) {
        return docs.size();
    }

    @Override
    public void swapAlias(String oldPhysicalName, String newPhysicalName) {
        currentPhysical.set(newPhysicalName);
    }

    @Override
    public void deleteIndex(String physicalName) {
        if (physicalName != null && physicalName.equals(currentPhysical.get())) {
            currentPhysical.set(null);
            docs.clear();
        }
    }

    // ── helper ─────────────────────────────────────────────

    private boolean matchKeyword(IndexDocument d, String keyword) {
        if (keyword.isBlank()) return true;
        String k = keyword.toLowerCase();
        return d.name().toLowerCase().contains(k) || d.brand().toLowerCase().contains(k);
    }

    private boolean matchFilters(IndexDocument d, List<FilterCriterion> filters) {
        for (FilterCriterion f : filters) {
            if (!matchOne(d, f)) return false;
        }
        return true;
    }

    private boolean matchOne(IndexDocument d, FilterCriterion f) {
        return switch (f) {
            case FilterCriterion.Term t -> readField(d, t.field()).equals(t.value());
            case FilterCriterion.Terms t -> t.values().contains(readField(d, t.field()));
            case FilterCriterion.Range r -> {
                long val = Long.parseLong(readField(d, r.field()));
                if (r.from() != null && (r.fromInclusive() ? val < r.from() : val <= r.from())) yield false;
                if (r.to() != null && (r.toInclusive() ? val > r.to() : val >= r.to())) yield false;
                yield true;
            }
            case FilterCriterion.Exists e -> !readField(d, e.field()).isEmpty();
        };
    }

    private String readField(IndexDocument d, String field) {
        return switch (field) {
            case "brand" -> d.brand();
            case "category" -> d.category();
            case "status" -> d.status();
            case "priceWon" -> String.valueOf(d.priceWon());
            case "stockQuantity" -> String.valueOf(d.stockQuantity());
            case "clickCount" -> String.valueOf(d.clickCount());
            default -> "";
        };
    }

    private List<FacetResult> computeFacets(List<IndexDocument> filtered, List<FacetSpec> specs) {
        List<FacetResult> result = new ArrayList<>();
        for (FacetSpec spec : specs) {
            switch (spec) {
                case FacetSpec.Terms t -> {
                    Map<String, Long> counts = new HashMap<>();
                    for (IndexDocument d : filtered) {
                        counts.merge(readField(d, t.field()), 1L, Long::sum);
                    }
                    List<FacetResult.Bucket> buckets = counts.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .limit(t.size())
                            .map(e -> new FacetResult.Bucket(e.getKey(), e.getValue()))
                            .toList();
                    result.add(new FacetResult(t.name(), buckets));
                }
                case FacetSpec.Range r -> {
                    List<FacetResult.Bucket> buckets = new ArrayList<>();
                    for (FacetSpec.Range.Bucket b : r.buckets()) {
                        long count = filtered.stream()
                                .filter(d -> {
                                    long val = Long.parseLong(readField(d, r.field()));
                                    if (b.from() != null && val < b.from()) return false;
                                    if (b.to() != null && val >= b.to()) return false;
                                    return true;
                                })
                                .count();
                        buckets.add(new FacetResult.Bucket(b.key(), count));
                    }
                    result.add(new FacetResult(r.name(), buckets));
                }
            }
        }
        return result;
    }
}
