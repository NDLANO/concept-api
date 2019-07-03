/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service.search

import java.lang.Math.max

import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.search.SearchResponse
import com.sksamuel.elastic4s.searches.sort.{FieldSort, SortOrder}
import com.typesafe.scalalogging.LazyLogging
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.index.IndexNotFoundException
import no.ndla.conceptapi.model.domain.{Language, NdlaSearchException, SearchResult, Sort}
import no.ndla.conceptapi.ConceptApiProperties.{ElasticSearchScrollKeepAlive, MaxPageSize}
import no.ndla.conceptapi.integration.Elastic4sClient

import scala.util.{Failure, Success, Try}

trait SearchService {
  this: Elastic4sClient with SearchConverterService with LazyLogging =>

  trait SearchService[T] {
    val searchIndex: String

    def scroll(scrollId: String, language: String): Try[SearchResult[T]] =
      e4sClient
        .execute {
          searchScroll(scrollId, ElasticSearchScrollKeepAlive)
        }
        .map(response => {
          val hits = getHits(response.result, language)

          SearchResult[T](
            totalCount = response.result.totalHits,
            page = None,
            pageSize = response.result.hits.hits.length,
            language = if (language == "*") Language.AllLanguages else language,
            results = hits,
            scrollId = response.result.scrollId
          )
        })

    def hitToApiModel(hit: String, language: String): T

    def getHits(response: SearchResponse, language: String): Seq[T] = {
      response.totalHits match {
        case count if count > 0 =>
          val resultArray = response.hits.hits

          resultArray.map(result => {
            val matchedLanguage = language match {
              case Language.AllLanguages | "*" =>
                searchConverterService.getLanguageFromHit(result).getOrElse(language)
              case _ => language
            }

            hitToApiModel(result.sourceAsString, matchedLanguage)
          })
        case _ => Seq()
      }
    }

    def getSortDefinition(sort: Sort.Value, language: String): FieldSort = {
      val sortLanguage = language match {
        case Language.NoLanguage => Language.DefaultLanguage
        case _                   => language
      }

      sort match {
        case Sort.ByTitleAsc =>
          language match {
            case "*" | Language.AllLanguages => fieldSort("defaultTitle").order(SortOrder.ASC).missing("_last")
            case _                           => fieldSort(s"title.$sortLanguage.raw").order(SortOrder.ASC).missing("_last")
          }
        case Sort.ByTitleDesc =>
          language match {
            case "*" | Language.AllLanguages => fieldSort("defaultTitle").order(SortOrder.DESC).missing("_last")
            case _                           => fieldSort(s"title.$sortLanguage.raw").order(SortOrder.DESC).missing("_last")
          }
        case Sort.ByRelevanceAsc    => fieldSort("_score").order(SortOrder.ASC)
        case Sort.ByRelevanceDesc   => fieldSort("_score").order(SortOrder.DESC)
        case Sort.ByLastUpdatedAsc  => fieldSort("lastUpdated").order(SortOrder.ASC).missing("_last")
        case Sort.ByLastUpdatedDesc => fieldSort("lastUpdated").order(SortOrder.DESC).missing("_last")
        case Sort.ByIdAsc           => fieldSort("id").order(SortOrder.ASC).missing("_last")
        case Sort.ByIdDesc          => fieldSort("id").order(SortOrder.DESC).missing("_last")
      }
    }

    def getSortDefinition(sort: Sort.Value): FieldSort = {
      sort match {
        case Sort.ByTitleAsc        => fieldSort("title.raw").order(SortOrder.ASC).missing("_last")
        case Sort.ByTitleDesc       => fieldSort("title.raw").order(SortOrder.DESC).missing("_last")
        case Sort.ByRelevanceAsc    => fieldSort("_score").order(SortOrder.ASC)
        case Sort.ByRelevanceDesc   => fieldSort("_score").order(SortOrder.DESC)
        case Sort.ByLastUpdatedAsc  => fieldSort("lastUpdated").order(SortOrder.ASC).missing("_last")
        case Sort.ByLastUpdatedDesc => fieldSort("lastUpdated").order(SortOrder.DESC).missing("_last")
        case Sort.ByIdAsc           => fieldSort("id").order(SortOrder.ASC).missing("_last")
        case Sort.ByIdDesc          => fieldSort("id").order(SortOrder.DESC).missing("_last")
      }
    }

    def countDocuments: Long = {
      val response = e4sClient.execute {
        catCount(searchIndex)
      }

      response match {
        case Success(resp) => resp.result.count
        case Failure(_)    => 0
      }
    }

    def getStartAtAndNumResults(page: Int, pageSize: Int): (Int, Int) = {
      val numResults = max(pageSize.min(MaxPageSize), 0)
      val startAt = (page - 1).max(0) * numResults

      (startAt, numResults)
    }

    protected def scheduleIndexDocuments(): Unit

    protected def errorHandler[U](failure: Throwable): Failure[U] = {
      failure match {
        case e: NdlaSearchException =>
          e.rf.status match {
            case notFound: Int if notFound == 404 =>
              logger.error(s"Index $searchIndex not found. Scheduling a reindex.")
              scheduleIndexDocuments()
              Failure(new IndexNotFoundException(s"Index $searchIndex not found. Scheduling a reindex"))
            case _ =>
              logger.error(e.getMessage)
              Failure(new ElasticsearchException(s"Unable to execute search in $searchIndex", e.getMessage))
          }
        case t: Throwable => Failure(t)
      }
    }
  }
}