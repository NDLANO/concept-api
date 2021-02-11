/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi

import com.typesafe.scalalogging.LazyLogging
import no.ndla.network.secrets.PropertyKeys
import no.ndla.network.secrets.Secrets.readSecrets
import no.ndla.network.{AuthUser, Domains}
import no.ndla.validation.ResourceType

import scala.util.Properties._
import scala.util.{Failure, Success}

object ConceptApiProperties extends LazyLogging {
  val IsKubernetes: Boolean = envOrNone("NDLA_IS_KUBERNETES").isDefined

  val Environment = propOrElse("NDLA_ENVIRONMENT", "local")
  val ApplicationName = "concept-api"

  val Auth0LoginEndpoint =
    s"https://${AuthUser.getAuth0HostForEnv(Environment)}/authorize"
  val ConceptRoleWithWriteAccess = "concept:write"

  val ApplicationPort = propOrElse("APPLICATION_PORT", "80").toInt
  val ContactEmail = "support+api@ndla.no"

  def MetaUserName = prop(PropertyKeys.MetaUserNameKey)
  def MetaPassword = prop(PropertyKeys.MetaPasswordKey)
  def MetaResource = prop(PropertyKeys.MetaResourceKey)
  def MetaServer = prop(PropertyKeys.MetaServerKey)
  def MetaPort = prop(PropertyKeys.MetaPortKey).toInt
  def MetaSchema = prop(PropertyKeys.MetaSchemaKey)
  val MetaMaxConnections = 10

  val resourceHtmlEmbedTag = "embed"
  val ApiClientsCacheAgeInMs: Long = 1000 * 60 * 60 // 1 hour caching

  val ArticleApiHost: String = propOrElse("ARTICLE_API_HOST", "article-api.ndla-local")
  val ImageApiHost: String = propOrElse("IMAGE_API_HOST", "image-api.ndla-local")
  val ApiGatewayHost: String = propOrElse("API_GATEWAY_HOST", "api-gateway.ndla-local")

  val SearchApiHost: String = propOrElse("SEARCH_API_HOST", "search-api.ndla-local")
  val SearchServer: String = propOrElse("SEARCH_SERVER", "http://search-concept-api.ndla-local")
  val SearchRegion: String = propOrElse("SEARCH_REGION", "eu-central-1")

  val RunWithSignedSearchRequests: Boolean = propOrElse("RUN_WITH_SIGNED_SEARCH_REQUESTS", "true").toBoolean

  val DraftConceptSearchIndex: String = propOrElse("CONCEPT_SEARCH_INDEX_NAME", "concepts")
  val PublishedConceptSearchIndex: String = propOrElse("PUBLISHED_CONCEPT_SEARCH_INDEX_NAME", "publishedconcepts")
  val ConceptSearchDocument = "concept"
  val DefaultPageSize = 10
  val MaxPageSize = 10000
  val IndexBulkSize = 250
  val ElasticSearchIndexMaxResultWindow = 10000
  val ElasticSearchScrollKeepAlive = "1m"
  val InitialScrollContextKeywords = List("0", "initial", "start", "first")

  val CorrelationIdKey = "correlationID"
  val CorrelationIdHeader = "X-Correlation-ID"

  lazy val Domain = Domains.get(Environment)

  lazy val H5PAddress = propOrElse(
    "NDLA_H5P_ADDRESS",
    Map(
      "test" -> "https://h5p-test.ndla.no",
      "staging" -> "https://h5p-staging.ndla.no"
    ).getOrElse(Environment, "https://h5p.ndla.no")
  )

  lazy val secrets = {
    val SecretsFile = "concept-api.secrets"
    readSecrets(SecretsFile) match {
      case Success(values) => values
      case Failure(exception) =>
        throw new RuntimeException(s"Unable to load remote secrets from $SecretsFile", exception)
    }
  }

  val externalApiUrls: Map[String, String] = Map(
    ResourceType.Image.toString -> s"$Domain/image-api/v2/images",
    "raw-image" -> s"$Domain/image-api/raw/id",
    ResourceType.H5P.toString -> H5PAddress
  )

  def booleanProp(key: String): Boolean = prop(key).toBoolean

  def prop(key: String): String = {
    propOrElse(key, throw new RuntimeException(s"Unable to load property $key"))
  }

  def propOpt(key: String): Option[String] = {
    propOrNone(key) match {
      case Some(prop)            => Some(prop)
      case None if !IsKubernetes => secrets.get(key).flatten
      case _                     => None
    }
  }

  def propOrElse(key: String, default: => String): String =
    propOpt(key).getOrElse(default)
}
