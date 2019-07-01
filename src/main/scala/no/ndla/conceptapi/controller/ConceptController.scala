/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.controller

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.model.api.{Error, NewConcept, UpdatedConcept}
import no.ndla.conceptapi.model.domain.Language
import no.ndla.conceptapi.service.{ReadService, WriteService}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.swagger.Swagger
import org.scalatra.{Created, NotFound, Ok}

import scala.util.{Failure, Success}

trait ConceptController {
  this: WriteService with ReadService with User =>
  val conceptController: ConceptController

  class ConceptController(implicit val swagger: Swagger) extends NdlaController with LazyLogging {
    protected implicit override val jsonFormats: Formats = DefaultFormats
    private val conceptId =
      Param[Long]("concept_id", "Id of the concept that is to be returned")
    protected val language: Param[Option[String]] =
      Param[Option[String]]("language", "The ISO 639-1 language code describing language.")
    protected val fallback: Param[Option[Boolean]] =
      Param[Option[Boolean]]("fallback", "Fallback to existing language if language is specified.")

    post("/") {
      doOrAccessDenied(user.getUser.canWrite) {
        val body = extract[NewConcept](request.body)
        body.flatMap(writeService.newConcept) match {
          case Success(c)  => Created(c)
          case Failure(ex) => errorHandler(ex)
        }
      }
    }

    patch("/:concept_id") {
      doOrAccessDenied(user.getUser.canWrite) {
        val body = extract[UpdatedConcept](request.body)
        val conceptId = long(this.conceptId.paramName)
        body.flatMap(writeService.updateConcept(conceptId, _)) match {
          case Success(c)  => Ok(c)
          case Failure(ex) => errorHandler(ex)
        }
      }
    }

    get("/:concept_id") {
      val conceptId = long(this.conceptId.paramName)
      val language =
        paramOrDefault(this.language.paramName, Language.NoLanguage)
      val fallback = booleanOrDefault(this.fallback.paramName, false)

      readService.conceptWithId(conceptId, language, fallback) match {
        case Success(concept) => Ok(concept)
        case Failure(ex)      => errorHandler(ex)
      }
    }
  }

}
