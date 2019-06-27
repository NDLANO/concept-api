/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */


package no.ndla.conceptapi.controller

import com.typesafe.scalalogging.LazyLogging
import org.scalatra.{NotFound, Ok}
import scalikejdbc._

import scala.util.{Failure, Success, Try}
import no.ndla.conceptapi.model.api.{Error, NewConcept}
import no.ndla.conceptapi.service.{ReadService, WriteService}
import no.ndla.conceptapi.auth.User
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.swagger.Swagger
import no.ndla.conceptapi.model.domain.Language

trait ConceptController
{
  this: WriteService with ReadService with User =>
  val conceptController: ConceptController

  class ConceptController(implicit val swagger: Swagger) extends NdlaController with LazyLogging
  {
    protected implicit override val jsonFormats: Formats = DefaultFormats
    private val conceptId = Param[Long]("concept_id", "Id of the concept that is to be returned")
    protected val language = Param[Option[String]]("language", "The ISO 639-1 language code describing language.")
    get("/"){
      Ok("Hello World")
    }
    post("/"){

      def newEmptyConcept(id: Long, externalIds: List[String])(implicit session: DBSession = AutoSession): Try[Long] = {
        Try(sql"""insert into conceptdata (id, external_id) values (55, 'hei')""".update.apply) match {
          case Success(_) =>
            logger.info(s"Inserted new empty article: $id")
            Success(id)
          case Failure(ex) => Failure(ex)
        }
      }
    newEmptyConcept(5,List.empty)


    }

      post("/a")
        {
        //doOrAccessDenied(user.getUser.canWrite) {
          val nid = params("externalId")
          extract[NewConcept](request.body).flatMap(writeService.newConcept(_, nid)) match {
            case Success(c)  => c
            case Failure(ex) => errorHandler(ex)
          }
        //}
    }
    get("/:concept_id"){
        val conceptId = long(this.conceptId.paramName)
        val language = paramOrDefault(this.language.paramName, Language.NoLanguage)
        readService.conceptWithId(conceptId, language) match {
          case Some(concept) => concept
          case None          => NotFound(body = Error(Error.NOT_FOUND, s"No concept with id $conceptId found"))
        }
      }
    }



}
