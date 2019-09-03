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
import no.ndla.conceptapi.model.api.listing._
import no.ndla.network.NdlaClient
import org.json4s.Formats
import scalaj.http.Http

import scala.math.ceil
import scala.util.{Failure, Success, Try}

case class ImageId(id: Long)
case class ImageAltText(alttext: String, language: String)
case class ImageWithAltText(alttext: ImageAltText)

trait ImageApiClient {
  this: NdlaClient with LazyLogging =>
  val imageApiClient: ImageApiClient

  class ImageApiClient {
    val baseUrl = s"http://${ConceptApiProperties.ImageApiHost}/"

    def getImageId(urlToImage: String): Try[Long] = {
      get[ImageId]("intern/id_from_url/", params = Map("url" -> urlToImage), 5000).map(_.id)
    }

    def getImageAltText(imageId: Long, language: String) = {
      get[ImageWithAltText](s"image-api/v2/images/$imageId", params = Map("language" -> language), 5000)
    }

    def get[T](path: String, params: Map[String, Any], timeout: Int)(implicit mf: Manifest[T]): Try[T] = {
      implicit val formats: Formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
      ndlaClient.fetchWithForwardedAuth[T](Http((baseUrl / path).addParams(params.toList)).timeout(timeout, timeout))
    }
  }

}
