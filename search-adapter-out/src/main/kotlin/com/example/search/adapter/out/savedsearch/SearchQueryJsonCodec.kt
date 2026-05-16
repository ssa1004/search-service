package com.example.search.adapter.out.savedsearch

import com.example.search.domain.query.FilterCriterion
import com.example.search.domain.query.Page
import com.example.search.domain.query.SearchQuery
import com.example.search.domain.query.SortSpec
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * [SearchQuery] ↔ JSON 변환. SavedSearch 컬럼 (`query_json`) 보관용.
 *
 * [FilterCriterion] 가 sealed 라 Jackson 의 default 직렬화로는 type 구분 불가 — 명시 type
 * 필드 (`_kind`) 를 사용한 manual 매핑.
 *
 * 형식 (예시):
 * ```
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
 * ```
 */
class SearchQueryJsonCodec(
    private val mapper: ObjectMapper
) {

    fun serialize(query: SearchQuery): String {
        val root = mapper.createObjectNode()
        root.put("keyword", query.keyword)

        val filters = root.putArray("filters")
        for (f in query.filters) {
            filters.add(serializeFilter(f))
        }

        val facets = root.putArray("facets")
        for (name in query.facets) {
            facets.add(name)
        }

        query.sort?.let { s ->
            val sortNode = root.putObject("sort")
            sortNode.put("field", s.field)
            sortNode.put("direction", s.direction.name)
        }

        val pageNode = root.putObject("page")
        pageNode.put("number", query.page.number)
        pageNode.put("size", query.page.size)

        try {
            return mapper.writeValueAsString(root)
        } catch (e: Exception) {
            throw IllegalStateException("SearchQuery 직렬화 실패", e)
        }
    }

    fun deserialize(json: String): SearchQuery {
        try {
            val root = mapper.readTree(json) as ObjectNode
            val keyword = root.path("keyword").asText("")

            val filters = ArrayList<FilterCriterion>()
            for (node in root.withArray("filters")) {
                filters.add(deserializeFilter(node as ObjectNode))
            }

            val facets: List<String> = mapper.convertValue(
                root.withArray("facets"),
                object : TypeReference<List<String>>() {}
            )

            var sort: SortSpec? = null
            if (root.hasNonNull("sort")) {
                val sortNode = root.get("sort") as ObjectNode
                sort = SortSpec(
                    sortNode.get("field").asText(),
                    SortSpec.Direction.valueOf(sortNode.get("direction").asText())
                )
            }

            val pageNode = root.get("page") as ObjectNode
            val page = Page(pageNode.get("number").asInt(), pageNode.get("size").asInt())

            return SearchQuery(keyword, filters, facets, sort, page)
        } catch (e: Exception) {
            throw IllegalStateException("SearchQuery 역직렬화 실패: $json", e)
        }
    }

    private fun serializeFilter(f: FilterCriterion): ObjectNode {
        val node = mapper.createObjectNode()
        when (f) {
            is FilterCriterion.Term -> {
                node.put("_kind", "term")
                node.put("field", f.field())
                node.put("value", f.value)
            }
            is FilterCriterion.Terms -> {
                node.put("_kind", "terms")
                node.put("field", f.field())
                val arr = node.putArray("values")
                f.values.forEach { arr.add(it) }
            }
            is FilterCriterion.Range -> {
                node.put("_kind", "range")
                node.put("field", f.field())
                f.from?.let { node.put("from", it) }
                node.put("fromInclusive", f.fromInclusive)
                f.to?.let { node.put("to", it) }
                node.put("toInclusive", f.toInclusive)
            }
            is FilterCriterion.Exists -> {
                node.put("_kind", "exists")
                node.put("field", f.field())
            }
        }
        return node
    }

    private fun deserializeFilter(node: ObjectNode): FilterCriterion {
        val kind = node.get("_kind").asText()
        val field = node.get("field").asText()
        return when (kind) {
            "term" -> FilterCriterion.Term(field, node.get("value").asText())
            "terms" -> {
                val values = ArrayList<String>()
                val it = node.withArray("values").elements()
                while (it.hasNext()) values.add(it.next().asText())
                FilterCriterion.Terms(field, values)
            }
            "range" -> FilterCriterion.Range(
                field,
                if (node.hasNonNull("from")) node.get("from").asLong() else null,
                node.path("fromInclusive").asBoolean(true),
                if (node.hasNonNull("to")) node.get("to").asLong() else null,
                node.path("toInclusive").asBoolean(false)
            )
            "exists" -> FilterCriterion.Exists(field)
            else -> throw IllegalArgumentException("알 수 없는 filter _kind: $kind")
        }
    }
}
