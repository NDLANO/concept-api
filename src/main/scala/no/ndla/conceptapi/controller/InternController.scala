/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.controller

import no.ndla.conceptapi.ConceptApiProperties
import no.ndla.conceptapi.service.search.{ConceptIndexService, IndexService}
import org.json4s.Formats
import org.scalatra.{InternalServerError, Ok, Unauthorized}
import org.scalatra.swagger.Swagger
import no.ndla.conceptapi.auth.{User, UserInfo}
import no.ndla.conceptapi.service.{ConverterService, ImportService}

import scala.util.{Failure, Success}

trait InternController {
  this: IndexService with ConceptIndexService with ImportService with ConverterService with User =>
  val internController: InternController

  class InternController(implicit val swagger: Swagger) extends NdlaController {
    protected val applicationDescription = "API for accessing internal functionality in draft API"
    protected implicit override val jsonFormats: Formats = org.json4s.DefaultFormats

    post("/index") {
      conceptIndexService.indexDocuments match {
        case Failure(ex) => errorHandler(ex)
        case Success(result) =>
          val msg = s"Completed indexing of ${result.totalIndexed} concepts in ${result.millisUsed} ms."
          logger.info(msg)
          Ok(msg)
      }
    }

    delete("/index") {
      def pluralIndex(n: Int) = if (n == 1) "1 index" else s"$n indexes"
      conceptIndexService.findAllIndexes(ConceptApiProperties.ConceptSearchIndex) match {
        case Failure(ex) =>
          logger.error("Could not find indexes to delete.")
          errorHandler(ex)
        case Success(indexesToDelete) =>
          val deleted = indexesToDelete.map(index => conceptIndexService.deleteIndexWithName(Some(index)))
          val (successes, errors) = deleted.partition(_.isSuccess)
          if (errors.nonEmpty) {
            val message = s"Failed to delete ${pluralIndex(errors.length)}: " +
              s"${errors.map(_.failed.get.getMessage).mkString(", ")}. " +
              s"${pluralIndex(successes.length)} were deleted successfully."
            halt(status = 500, body = message)
          } else {
            Ok(body = s"Deleted ${pluralIndex(successes.length)}")
          }
      }
    }

    post("/import/liste") {
      UserInfo.get match {
        case Some(x) if x.canWrite =>
          val start = System.currentTimeMillis
          val forceUpdate = booleanOrDefault("forceUpdate", default = false)

        case _ => Unauthorized("You do not have access to perform this action")
      }
    }

    post("/import/concept") {
      UserInfo.get match {
        case Some(x) if x.canWrite =>
          val start = System.currentTimeMillis
          val forceUpdate = booleanOrDefault("forceUpdate", default = false)

          importService.importConcepts(forceUpdate) match {
            case Success(result) =>
              if (result.numSuccessfullyImportedConcepts < result.totalAttemptedImportedConcepts) {
                InternalServerError(result)
              } else {
                Ok(result)
              }
            case Failure(ex) =>
              val errMsg =
                s"Import of concepts failed after ${System.currentTimeMillis - start} ms with error: ${ex.getMessage}\n"
              logger.warn(errMsg, ex)
              InternalServerError(body = errMsg)
          }
        case _ => Unauthorized("You do not have access to perform this action")
      }

    }

  }
}
