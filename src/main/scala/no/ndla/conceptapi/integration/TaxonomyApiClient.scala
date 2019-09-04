/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.integration

import com.typesafe.scalalogging.LazyLogging
import io.lemonlabs.uri.dsl._
import no.ndla.conceptapi.ConceptApiProperties
import no.ndla.network.NdlaClient
import org.json4s.Formats
import scalaj.http.Http

import scala.util.Try

case class TaxonomyResource(paths: List[String])

trait TaxonomyApiClient {
  this: NdlaClient with LazyLogging =>
  val taxonomyApiClient: TaxonomyApiClient

  class TaxonomyApiClient {
    val baseUrl = s"http://${ConceptApiProperties.ApiGatewayHost}/taxonomy/v1"

    private def getResourceByNodeId(oldNodeId: Long) = {
      val resourceId = s"urn:resource:1:$oldNodeId"
      get[Option[TaxonomyResource]](s"resources/$resourceId", params = Map.empty, 5000)
    }

    private def getTopicByNodeId(oldNodeId: Long) = {
      val topicId = s"urn:topic:1:$oldNodeId"
      get[Option[TaxonomyResource]](s"topics/$topicId", params = Map.empty, 5000)
    }

    private def queryResourceByArticleApiId(id: Long) = {
      val contentUri = s"urn:article:$id"
      get[List[TaxonomyResource]](s"queries/resources", params = Map("contentURI" -> contentUri), 5000)
    }

    private def queryTopicByArticleApiId(id: Long) = {
      val contentUri = s"urn:article:$id"
      get[List[TaxonomyResource]](s"queries/topics", params = Map("contentURI" -> contentUri), 5000)
    }

    private def getAllPossibleResources(oldNodeId: Option[Long], articleApiId: Long) = {
      val directlyFetched = oldNodeId match {
        case Some(oid) =>
          val resource = getResourceByNodeId(oid)
          val topic = getTopicByNodeId(oid)
          resource.getOrElse(None).toList ++ topic.getOrElse(None).toList
        case None => List.empty
      }

      val queriedResources = queryResourceByArticleApiId(articleApiId)
      val queriedTopics = queryTopicByArticleApiId(articleApiId)
      val queried = queriedResources.getOrElse(List.empty) ++ queriedTopics.getOrElse(List.empty)

      (directlyFetched ++ queried).distinct
    }

    def getSubjectIdsForIds(oldNodeId: Option[Long], articleApiId: Long): Set[String] = {
      getAllPossibleResources(oldNodeId, articleApiId)
        .flatMap(_.paths)
        .flatMap(extractSubjectFromTaxonomyPath)
        .toSet
    }

    private def extractSubjectFromTaxonomyPath(path: String) = {
      path
        .split('/')
        .find(part => part.contains("subject:"))
        .map("urn:" + _)
    }

    def get[T](path: String, params: Map[String, Any], timeout: Int)(implicit mf: Manifest[T]): Try[T] = {
      implicit val formats: Formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
      ndlaClient.fetchWithForwardedAuth[T](Http((baseUrl / path).addParams(params.toList)).timeout(timeout, timeout))
    }
  }

}
