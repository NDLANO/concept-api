/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.integration

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.ConceptApiProperties
import no.ndla.conceptapi.model.api.listing._
import no.ndla.network.NdlaClient
import org.json4s.Formats
import scalaj.http.Http
import io.lemonlabs.uri.dsl._

import scala.math.ceil
import scala.util.{Failure, Success, Try}

trait ListingApiClient {
  this: NdlaClient with LazyLogging =>
  val listingApiClient: ListingApiClient

  class ListingApiClient {
    val baseUrl = s"http://${ConceptApiProperties.ListingApiHost}"
    val dumpDomainPath = "intern/dump/cover"

    def getChunks: Iterator[Try[Seq[Cover]]] = {
      getChunk(0, 0) match {
        case Success(initSearch) =>
          val dbCount = initSearch.totalCount
          val pageSize = ConceptApiProperties.IndexBulkSize
          val numPages = ceil(dbCount.toDouble / pageSize.toDouble).toInt
          val pages = Seq.range(1, numPages + 1)

          val iterator: Iterator[Try[Seq[Cover]]] = pages.toIterator.map(p => {
            getChunk(p, pageSize).map(_.results)
          })

          iterator
        case Failure(ex) =>
          logger.error(s"Could not fetch initial chunk from ${baseUrl / dumpDomainPath}")
          Iterator(Failure(ex))
      }
    }

    def get[T](path: String, params: Map[String, Any], timeout: Int)(implicit mf: Manifest[T]): Try[T] = {
      implicit val formats: Formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
      ndlaClient.fetchWithForwardedAuth[T](Http((baseUrl / path).addParams(params.toList)).timeout(timeout, timeout))
    }

    private def getChunk(page: Int, pageSize: Int): Try[CoverDomainDump] = {
      val params = Map(
        "page" -> page,
        "page-size" -> pageSize
      )

      get[CoverDomainDump](dumpDomainPath, params, timeout = 20000) match {
        case Success(result) =>
          logger.info(s"Fetched chunk of ${result.results.size} concepts from ${baseUrl.addParams(params)}")
          Success(result)
        case Failure(ex) =>
          logger.error(
            s"Could not fetch chunk on page: '$page', with pageSize: '$pageSize' from '${baseUrl / dumpDomainPath}'")
          Failure(ex)
      }
    }

  }

}
