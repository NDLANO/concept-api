/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.controller

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.model.api.{Concept, Error, NewConcept, UpdatedConcept, ValidationError}
import no.ndla.conceptapi.model.domain.Language
import no.ndla.conceptapi.service.{ReadService, WriteService}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.swagger.{ResponseMessage, Swagger, SwaggerSupport}
import org.scalatra.{NotFound, Ok}

import scala.util.{Failure, Success}

trait ConceptController {
  this: WriteService with ReadService with User =>
  val conceptController: ConceptController

  class ConceptController(implicit val swagger: Swagger) extends NdlaController with SwaggerSupport with LazyLogging {
    protected implicit override val jsonFormats: Formats = DefaultFormats
    private val conceptId =
      Param[Long]("concept_id", "Id of the concept that is to be returned")
    protected val language: Param[Option[String]] =
      Param[Option[String]]("language", "The ISO 639-1 language code describing language.")

    val applicationDescription = "This is the Api for concepts"

    // Additional models used in error responses
    registerModel[ValidationError]()
    registerModel[Error]()

    val response400 =
      ResponseMessage(400, "Validation Error", Some("ValidationError"))
    val response403 = ResponseMessage(403, "Access Denied", Some("Error"))
    val response404 = ResponseMessage(404, "Not found", Some("Error"))
    val response500 = ResponseMessage(500, "Unknown error", Some("Error"))

    post(
      "/",
      operation(
        apiOperation[Concept]("newConceptById")
          summary "Create new concept"
          description "Create new concept"
          parameters (
            asHeaderParam(correlationId),
            bodyParam[NewConcept]
        )
          authorizations "oauth2"
          responseMessages (response400, response403, response500))
    ) {
      doOrAccessDenied(user.getUser.canWrite) {
        extract[NewConcept](request.body)
          .flatMap(writeService.newConcept) match {
          case Success(c)  => c
          case Failure(ex) => errorHandler(ex)
        }
      }
    }

    patch(
      "/:concept_id",
      operation(
        apiOperation[Concept]("updateConceptById")
          summary "Update a concept"
          description "Update a concept"
          parameters (
            asHeaderParam(correlationId),
            bodyParam[UpdatedConcept],
            asPathParam(conceptId)
        )
          authorizations "oauth2"
          responseMessages (response400, response403, response404, response500))
    ) {
      doOrAccessDenied(user.getUser.canWrite) {
        extract[UpdatedConcept](request.body)
          .flatMap(
            writeService
              .updateConcept(long(this.conceptId.paramName), _)) match {
          case Success(c)  => c
          case Failure(ex) => errorHandler(ex)
        }
      }
    }

    get(
      "/:concept_id",
      operation(
        apiOperation[String]("getConceptById")
          summary "Show concept with a specified id"
          description "Shows the concept for the specified id."
          parameters (
            asHeaderParam(correlationId),
            asQueryParam(language),
            asPathParam(conceptId)
        )
          authorizations "oauth2"
          responseMessages (response404, response500))
    ) {
      val conceptId = long(this.conceptId.paramName)
      val language =
        paramOrDefault(this.language.paramName, Language.NoLanguage)
      readService.conceptWithId(conceptId, language) match {
        case Some(concept) => concept
        case None =>
          NotFound(body = Error(Error.NOT_FOUND, s"No concept with id $conceptId found"))
      }
    }
  }

}
