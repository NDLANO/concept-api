/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */


package no.ndla.conceptapi.controller

import com.typesafe.scalalogging.LazyLogging
import org.scalatra.Ok
import scalikejdbc._

import scala.util.{Failure, Success, Try}
import no.ndla.conceptapi.model.api.{Concept, NewConcept}
import no.ndla.conceptapi.service.WriteService
import no.ndla.conceptapi.auth.User
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.swagger.Swagger

trait ConceptController
{
  this: WriteService with User =>
  val conceptController: ConceptController

  class ConceptController(implicit val swagger: Swagger) extends NdlaController with LazyLogging
  {
    protected implicit override val jsonFormats: Formats = DefaultFormats

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

  }

}
