package com.example.search.adapter.out.savedsearch;

import com.example.search.domain.query.FilterCriterion;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.query.SortSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * {@link SearchQuery} ↔ JSON 변환. SavedSearch 컬럼 ({@code query_json}) 보관용.
 *
 * <p>{@link FilterCriterion} 가 sealed 라 Jackson 의 default 직렬화로는 type 구분 불가 — 명시 type
 * 필드 ({@code _kind}) 를 사용한 manual 매핑.</p>
 *
 * <p>형식 (예시):</p>
 * <pre>{@code
 * {
 *   "keyword": "nike",
 *   "filters": [
 *     {"_kind":"term", "field":"brand", "value":"nike"},
 *     {"_kind":"range", "field":"priceWon", "from":0, "fromInclusive":true,
 *      "to":300000, "toInclusive":false}
 *   ],
 *   "facets": ["brand"],
 *   "sort": {"field":"releasedAt","direction":"DESC"},
 *   "page": {"number":0, "size":20}
 * }
 * }</pre>
 */
@RequiredArgsConstructor
public class SearchQueryJsonCodec {

    private final ObjectMapper mapper;

    public String serialize(SearchQuery query) {
        ObjectNode root = mapper.createObjectNode();
        root.put("keyword", query.keyword());

        ArrayNode filters = root.putArray("filters");
        for (FilterCriterion f : query.filters()) {
            filters.add(serializeFilter(f));
        }

        ArrayNode facets = root.putArray("facets");
        for (String name : query.facets()) {
            facets.add(name);
        }

        if (query.sort() != null) {
            ObjectNode sort = root.putObject("sort");
            sort.put("field", query.sort().field());
            sort.put("direction", query.sort().direction().name());
        }

        ObjectNode page = root.putObject("page");
        page.put("number", query.page().number());
        page.put("size", query.page().size());

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("SearchQuery 직렬화 실패", e);
        }
    }

    public SearchQuery deserialize(String json) {
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(json);
            String keyword = root.path("keyword").asText("");

            List<FilterCriterion> filters = new ArrayList<>();
            for (var node : root.withArray("filters")) {
                filters.add(deserializeFilter((ObjectNode) node));
            }

            List<String> facets = mapper.convertValue(
                    root.withArray("facets"), new TypeReference<List<String>>() {});

            SortSpec sort = null;
            if (root.hasNonNull("sort")) {
                ObjectNode sortNode = (ObjectNode) root.get("sort");
                sort = new SortSpec(
                        sortNode.get("field").asText(),
                        SortSpec.Direction.valueOf(sortNode.get("direction").asText()));
            }

            ObjectNode pageNode = (ObjectNode) root.get("page");
            Page page = new Page(pageNode.get("number").asInt(), pageNode.get("size").asInt());

            return new SearchQuery(keyword, filters, facets, sort, page);
        } catch (Exception e) {
            throw new IllegalStateException("SearchQuery 역직렬화 실패: " + json, e);
        }
    }

    private ObjectNode serializeFilter(FilterCriterion f) {
        ObjectNode node = mapper.createObjectNode();
        switch (f) {
            case FilterCriterion.Term t -> {
                node.put("_kind", "term");
                node.put("field", t.field());
                node.put("value", t.value());
            }
            case FilterCriterion.Terms t -> {
                node.put("_kind", "terms");
                node.put("field", t.field());
                ArrayNode arr = node.putArray("values");
                t.values().forEach(arr::add);
            }
            case FilterCriterion.Range r -> {
                node.put("_kind", "range");
                node.put("field", r.field());
                if (r.from() != null) node.put("from", r.from());
                node.put("fromInclusive", r.fromInclusive());
                if (r.to() != null) node.put("to", r.to());
                node.put("toInclusive", r.toInclusive());
            }
            case FilterCriterion.Exists e -> {
                node.put("_kind", "exists");
                node.put("field", e.field());
            }
        }
        return node;
    }

    private FilterCriterion deserializeFilter(ObjectNode node) {
        String kind = node.get("_kind").asText();
        String field = node.get("field").asText();
        return switch (kind) {
            case "term" -> new FilterCriterion.Term(field, node.get("value").asText());
            case "terms" -> {
                List<String> values = new ArrayList<>();
                Iterator<com.fasterxml.jackson.databind.JsonNode> it = node.withArray("values").elements();
                while (it.hasNext()) values.add(it.next().asText());
                yield new FilterCriterion.Terms(field, values);
            }
            case "range" -> new FilterCriterion.Range(
                    field,
                    node.hasNonNull("from") ? node.get("from").asLong() : null,
                    node.path("fromInclusive").asBoolean(true),
                    node.hasNonNull("to") ? node.get("to").asLong() : null,
                    node.path("toInclusive").asBoolean(false));
            case "exists" -> new FilterCriterion.Exists(field);
            default -> throw new IllegalArgumentException("알 수 없는 filter _kind: " + kind);
        };
    }
}
