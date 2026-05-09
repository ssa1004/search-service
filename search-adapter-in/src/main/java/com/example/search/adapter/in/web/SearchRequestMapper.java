package com.example.search.adapter.in.web;

import com.example.search.adapter.in.web.dto.SearchDtos;
import com.example.search.application.command.SearchProductCommand;
import com.example.search.domain.facet.FacetSpec;
import com.example.search.domain.index.BoostRule;
import com.example.search.domain.query.FilterCriterion;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.query.SortSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * REST DTO → 도메인 명령 매핑. 컨트롤러에서 분리해 단위 테스트 가능하게 한다.
 */
public final class SearchRequestMapper {

    private SearchRequestMapper() {
    }

    public static SearchProductCommand toCommand(SearchDtos.SearchRequest req) {
        SearchQuery query = new SearchQuery(
                req.keyword() == null ? "" : req.keyword(),
                toFilters(req.filters()),
                toFacetNames(req.facets()),
                toSort(req.sort()),
                new Page(req.page(), req.size())
        );
        return new SearchProductCommand(query, toFacetSpecs(req.facets()), BoostRule.defaults());
    }

    private static List<FilterCriterion> toFilters(List<SearchDtos.FilterDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return List.of();
        List<FilterCriterion> result = new ArrayList<>(dtos.size());
        for (SearchDtos.FilterDto d : dtos) {
            result.add(toFilter(d));
        }
        return result;
    }

    private static FilterCriterion toFilter(SearchDtos.FilterDto d) {
        return switch (d.op()) {
            case "term" -> new FilterCriterion.Term(d.field(), d.value());
            case "terms" -> new FilterCriterion.Terms(d.field(), d.values());
            case "range" -> new FilterCriterion.Range(d.field(), d.from(),
                    d.fromInclusive() == null || d.fromInclusive(),
                    d.to(),
                    d.toInclusive() != null && d.toInclusive());
            case "exists" -> new FilterCriterion.Exists(d.field());
            default -> throw new IllegalArgumentException("알 수 없는 filter op: " + d.op());
        };
    }

    private static List<String> toFacetNames(List<SearchDtos.FacetSpecDto> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream().map(SearchDtos.FacetSpecDto::name).toList();
    }

    private static List<FacetSpec> toFacetSpecs(List<SearchDtos.FacetSpecDto> dtos) {
        if (dtos == null) return List.of();
        List<FacetSpec> result = new ArrayList<>(dtos.size());
        for (SearchDtos.FacetSpecDto d : dtos) {
            result.add(toFacetSpec(d));
        }
        return result;
    }

    private static FacetSpec toFacetSpec(SearchDtos.FacetSpecDto d) {
        return switch (d.type()) {
            case "terms" -> new FacetSpec.Terms(d.name(), d.field(),
                    d.size() == null ? 10 : d.size());
            case "range" -> {
                List<FacetSpec.Range.Bucket> buckets = new ArrayList<>();
                for (SearchDtos.FacetSpecDto.RangeBucketDto b : d.buckets()) {
                    buckets.add(new FacetSpec.Range.Bucket(b.key(), b.from(), b.to()));
                }
                yield new FacetSpec.Range(d.name(), d.field(), buckets);
            }
            default -> throw new IllegalArgumentException("알 수 없는 facet type: " + d.type());
        };
    }

    private static SortSpec toSort(SearchDtos.SortDto sort) {
        if (sort == null) return null;
        return new SortSpec(sort.field(),
                "desc".equalsIgnoreCase(sort.direction()) ? SortSpec.Direction.DESC : SortSpec.Direction.ASC);
    }
}
