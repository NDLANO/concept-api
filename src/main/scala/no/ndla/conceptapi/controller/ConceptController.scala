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

trait ConceptController
{

  class ConceptController extends NdlaController with LazyLogging
  {
    get("/"){
      Ok("Hello World")
    }
    post("/"){

      def newEmptyConcept(id: Long, externalIds: List[String])(implicit session: DBSession = AutoSession): Try[Long] = {
        Try(sql"""insert into conceptdata (id, external_id) values (2, 'hei')""".update.apply) match {
          case Success(_) =>
            logger.info(s"Inserted new empty article: $id")
            Success(id)
          case Failure(ex) => Failure(ex)
        }
      }
    newEmptyConcept(5,List.empty)


    }

  }

}
