/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service.search

import java.util.concurrent.Executors

import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.queries.BoolQuery
import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.integration.Elastic4sClient
import no.ndla.conceptapi.service.ConverterService
import no.ndla.conceptapi.ConceptApiProperties
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.api.ResultWindowTooLargeException
import no.ndla.conceptapi.model.domain.{Language, SearchResult, Sort}
import no.ndla.conceptapi.model.search.SearchSettings
import no.ndla.mapping.ISO639

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.util.{Failure, Success, Try}

trait ConceptSearchService {
  this: Elastic4sClient with SearchService with ConceptIndexService with ConverterService with SearchConverterService =>
  val conceptSearchService: ConceptSearchService

  class ConceptSearchService extends LazyLogging with SearchService[api.ConceptSummary] {
    override val searchIndex: String = ConceptApiProperties.ConceptSearchIndex

    override def hitToApiModel(hitString: String, language: String): api.ConceptSummary =
      searchConverterService.hitAsConceptSummary(hitString, language)

    def all(settings: SearchSettings): Try[SearchResult[api.ConceptSummary]] = executeSearch(boolQuery(), settings)

    def matchingQuery(query: String, settings: SearchSettings): Try[SearchResult[api.ConceptSummary]] = {
      val language = if (settings.searchLanguage == Language.AllLanguages) "*" else settings.searchLanguage

      val titleSearch = simpleStringQuery(query).field(s"title.$language", 2)
      val contentSearch = simpleStringQuery(query).field(s"content.$language", 1)
      val tagSearch = simpleStringQuery(query).field(s"tags.$language", 1)

      val fullQuery = boolQuery()
        .must(
          boolQuery()
            .should(
              titleSearch,
              contentSearch,
              tagSearch
            ))

      executeSearch(fullQuery, settings)
    }

    def executeSearch(queryBuilder: BoolQuery, settings: SearchSettings): Try[SearchResult[api.ConceptSummary]] = {
      val idFilter = if (settings.withIdIn.isEmpty) None else Some(idsQuery(settings.withIdIn))

      val (languageFilter, searchLanguage) = settings.searchLanguage match {
        case "" | Language.AllLanguages | "*" =>
          (None, "*")
        case lang =>
          if (settings.fallback)
            (None, "*")
          else
            (Some(existsQuery(s"title.$lang")), lang)
      }

      val subjectFilter =
        if (settings.subjectIds.isEmpty) None
        else
          Some(
            boolQuery()
              .should(
                settings.subjectIds.map(
                  si => termQuery("subjectIds", si)
                )
              ))

      val tagFilter =
        if (settings.tagsToFilterBy.isEmpty) None
        else
          Some(
            boolQuery()
              .should(
                settings.tagsToFilterBy
                  .flatMap(t =>
                    ISO639.languagePriority // Since termQuery doesn't support wildcard in field we need to create one for each language.
                      .map(l => termQuery(s"tags.$l.raw", t)))
              ))

      val filters = List(idFilter, languageFilter, subjectFilter, tagFilter)
      val filteredSearch = queryBuilder.filter(filters.flatten)

      val (startAt, numResults) = getStartAtAndNumResults(settings.page, settings.pageSize)
      val requestedResultWindow = settings.pageSize * settings.page
      if (requestedResultWindow > ConceptApiProperties.ElasticSearchIndexMaxResultWindow) {
        logger.info(
          s"Max supported results are ${ConceptApiProperties.ElasticSearchIndexMaxResultWindow}, user requested $requestedResultWindow")
        Failure(new ResultWindowTooLargeException())
      } else {
        val searchToExecute =
          search(searchIndex)
            .size(numResults)
            .from(startAt)
            .query(filteredSearch)
            .highlighting(highlight("*"))
            .sortBy(getSortDefinition(settings.sort, searchLanguage))

        val searchWithScroll =
          if (startAt != 0) { searchToExecute } else {
            searchToExecute.scroll(ConceptApiProperties.ElasticSearchScrollKeepAlive)
          }

        e4sClient.execute(searchWithScroll) match {
          case Success(response) =>
            Success(
              SearchResult(
                response.result.totalHits,
                Some(settings.page),
                numResults,
                if (searchLanguage == "*") Language.AllLanguages else searchLanguage,
                getHits(response.result, settings.searchLanguage),
                response.result.scrollId
              ))
          case Failure(ex) =>
            errorHandler(ex)
        }
      }
    }

    override def scheduleIndexDocuments(): Unit = {
      implicit val ec: ExecutionContextExecutorService =
        ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor)
      val f = Future {
        conceptIndexService.indexDocuments
      }

      f.failed.foreach(t => logger.warn("Unable to create index: " + t.getMessage, t))
      f.foreach {
        case Success(reindexResult) =>
          logger.info(
            s"Completed indexing of ${reindexResult.totalIndexed} concepts in ${reindexResult.millisUsed} ms.")
        case Failure(ex) => logger.warn(ex.getMessage, ex)
      }
    }

  }
}
