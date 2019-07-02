/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service.search

import com.sksamuel.elastic4s.http.search.SearchHit
import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.model.domain.Language
import no.ndla.conceptapi.service.ConverterService
import no.ndla.mapping.ISO639
import no.ndla.network.ApplicationUrl
import org.joda.time.DateTime
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read
import org.jsoup.Jsoup
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.api.ConceptSearchResult
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.domain.{Concept, SearchResult}, Language.getSupportedLanguages
import no.ndla.conceptapi.model.search.{
  LanguageValue,
  SearchableConcept,
  SearchableLanguageFormats,
  SearchableLanguageValues
}

trait SearchConverterService {
  this: ConverterService =>
  val searchConverterService: SearchConverterService

  class SearchConverterService extends LazyLogging {
    implicit val formats: Formats = SearchableLanguageFormats.JSonFormats

    def asSearchableConcept(c: Concept): SearchableConcept = {
      val defaultTitle = c.title
        .sortBy(title => {
          val languagePriority = Language.languageAnalyzers.map(la => la.lang).reverse
          languagePriority.indexOf(title.language)
        })
        .lastOption

      SearchableConcept(
        id = c.id.get,
        title = SearchableLanguageValues(c.title.map(title => LanguageValue(title.language, title.title))),
        content = SearchableLanguageValues(c.content.map(content => LanguageValue(content.language, content.content))),
        defaultTitle = defaultTitle.map(_.title)
      )
    }

    def hitAsConceptSummary(hitString: String, language: String): api.ConceptSummary = {

      val searchableConcept = read[SearchableConcept](hitString)
      val titles = searchableConcept.title.languageValues.map(lv => domain.ConceptTitle(lv.value, lv.language))
      val contents = searchableConcept.content.languageValues.map(lv => domain.ConceptContent(lv.value, lv.language))

      val supportedLanguages = getSupportedLanguages(Seq(titles, contents))

      val title = Language
        .findByLanguageOrBestEffort(titles, language)
        .map(converterService.toApiConceptTitle)
        .getOrElse(api.ConceptTitle("", Language.UnknownLanguage))
      val concept = Language
        .findByLanguageOrBestEffort(contents, language)
        .map(converterService.toApiConceptContent)
        .getOrElse(api.ConceptContent("", Language.UnknownLanguage))

      api.ConceptSummary(
        searchableConcept.id,
        title,
        concept,
        supportedLanguages
      )
    }

    /**
      * Attempts to extract language that hit from highlights in elasticsearch response.
      *
      * @param result Elasticsearch hit.
      * @return Language if found.
      */
    def getLanguageFromHit(result: SearchHit): Option[String] = {
      def keyToLanguage(keys: Iterable[String]): Option[String] = {
        val keyLanguages = keys.toList.flatMap(key =>
          key.split('.').toList match {
            case _ :: language :: _ => Some(language)
            case _                  => None
        })

        keyLanguages
          .sortBy(lang => {
            ISO639.languagePriority.reverse.indexOf(lang)
          })
          .lastOption
      }

      val highlightKeys: Option[Map[String, _]] = Option(result.highlight)
      val matchLanguage = keyToLanguage(highlightKeys.getOrElse(Map()).keys)

      matchLanguage match {
        case Some(lang) =>
          Some(lang)
        case _ =>
          keyToLanguage(result.sourceAsMap.keys)
      }
    }

    def asApiConceptSearchResult(searchResult: SearchResult[api.ConceptSummary]): ConceptSearchResult =
      api.ConceptSearchResult(searchResult.totalCount,
                              searchResult.page,
                              searchResult.pageSize,
                              searchResult.language,
                              searchResult.results)

  }
}
