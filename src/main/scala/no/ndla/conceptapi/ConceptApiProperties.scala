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

  lazy val MetaUserName = prop(PropertyKeys.MetaUserNameKey)
  lazy val MetaPassword = prop(PropertyKeys.MetaPasswordKey)
  lazy val MetaResource = prop(PropertyKeys.MetaResourceKey)
  lazy val MetaServer = prop(PropertyKeys.MetaServerKey)
  lazy val MetaPort = prop(PropertyKeys.MetaPortKey).toInt
  lazy val MetaSchema = prop(PropertyKeys.MetaSchemaKey)
  val MetaMaxConnections = 10

  val resourceHtmlEmbedTag = "embed"
  val ApiClientsCacheAgeInMs: Long = 1000 * 60 * 60 // 1 hour caching

  val ArticleApiHost: String = propOrElse("ARTICLE_API_HOST", "article-api.ndla-local")
  val LearningpathApiHost: String = propOrElse("LEARNINGPATH_API_HOST", "learningpath-api.ndla-local")
  val AudioApiHost: String = propOrElse("AUDIO_API_HOST", "audio-api.ndla-local")
  val DraftApiHost: String = propOrElse("DRAFT_API_HOST", "draft-api.ndla-local")
  val ImageApiHost: String = propOrElse("IMAGE_API_HOST", "image-api.ndla-local")

  val SearchApiHost: String = propOrElse("SEARCH_API_HOST", "search-api.ndla-local")
  val SearchServer: String = propOrElse("SEARCH_SERVER", "http://search-concept-api.ndla-local")
  val SearchRegion: String = propOrElse("SEARCH_REGION", "eu-central-1")

  val RunWithSignedSearchRequests: Boolean = propOrElse("RUN_WITH_SIGNED_SEARCH_REQUESTS", "true").toBoolean

  val ConceptSearchIndex: String = propOrElse("CONCEPT_SEARCH_INDEX_NAME", "concepts")
  val ConceptSearchDocument = "concept"
  val DefaultPageSize = 10
  val MaxPageSize = 1000
  val IndexBulkSize = 250
  val ElasticSearchIndexMaxResultWindow = 10000
  val ElasticSearchScrollKeepAlive = "10s"

  val CorrelationIdKey = "correlationID"
  val CorrelationIdHeader = "X-Correlation-ID"

  lazy val Domain = Domains.get(Environment)

  lazy val secrets = {
    val SecretsFile = "concept-api.secrets"
    readSecrets(SecretsFile) match {
      case Success(values) => values
      case Failure(exception) =>
        throw new RuntimeException(s"Unable to load remote secrets from $SecretsFile", exception)
    }
  }

  val externalApiUrls: Map[String, String] = Map(
    "raw-image" -> s"$Domain/image-api/raw/id"
  )

  def booleanProp(key: String): Boolean = prop(key).toBoolean

  def prop(key: String): String = {
    propOrElse(key, throw new RuntimeException(s"Unable to load property $key"))
  }

  def propOpt(key: String): Option[String] = {
    envOrNone(key) match {
      case Some(prop)            => Some(prop)
      case None if !IsKubernetes => secrets.get(key).flatten
      case _                     => None
    }
  }

  def propOrElse(key: String, default: => String): String =
    propOpt(key).getOrElse(default)
}
