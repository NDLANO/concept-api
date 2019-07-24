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
import org.scalatra.{GatewayTimeout, InternalServerError, Ok, Unauthorized}
import org.scalatra.swagger.Swagger
import no.ndla.conceptapi.auth.{User, UserInfo}
import no.ndla.conceptapi.model.S3UploadException
import no.ndla.conceptapi.service.{ConverterService, ImportService}
import no.ndla.conceptapi.model.api.NDLAErrors

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

    post("/import") {
      UserInfo.get match {
        case Some(value) =>
          val start = System.currentTimeMillis

          importService.importConcepts() match {
            case Success(concept) => {
              Ok(converterService.asApiImageMetaInformationWithDomainUrlV2(imageMeta, None))
            }

            case Failure(ex: Throwable) => {
              val errMsg =
                s"Import of concepts failed after ${System.currentTimeMillis - start} ms with error: ${ex.getMessage}\n"
              logger.warn(errMsg, ex)
              InternalServerError(body = errMsg)
            }
          }
        case None => Unauthorized("No access for u")
      }

    }

    get("/dump/concept") {
      // Dumps Domain articles
      val pageNo = intOrDefault("page", 1)
      val pageSize = intOrDefault("page-size", 250)

      // readService.getArticleDomainDump(pageNo, pageSize)
    }

  }
}
