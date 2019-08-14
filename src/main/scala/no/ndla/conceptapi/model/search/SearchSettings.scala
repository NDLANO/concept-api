package no.ndla.conceptapi.model.search

import no.ndla.conceptapi.model.domain.Sort

case class SearchSettings(
    withIdIn: List[Long],
    searchLanguage: String,
    page: Int,
    pageSize: Int,
    sort: Sort.Value,
    fallback: Boolean,
    subjectIds: Set[String],
    tagsToFilterBy: Set[String]
)
