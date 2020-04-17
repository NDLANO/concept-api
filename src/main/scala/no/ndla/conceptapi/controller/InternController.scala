/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.controller

import java.util.concurrent.{Executor, Executors}

import no.ndla.conceptapi.ConceptApiProperties
import no.ndla.conceptapi.service.search.{DraftConceptIndexService, IndexService, PublishedConceptIndexService}
import org.json4s.Formats
import org.scalatra.{InternalServerError, Ok, Unauthorized}
import org.scalatra.swagger.Swagger
import no.ndla.conceptapi.auth.{User, UserInfo}
import no.ndla.conceptapi.model.domain.ReindexResult
import no.ndla.conceptapi.service.{ConverterService, ImportService}

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait InternController {
  this: IndexService
    with DraftConceptIndexService
    with PublishedConceptIndexService
    with ImportService
    with ConverterService
    with User =>
  val internController: InternController

  class InternController(implicit val swagger: Swagger) extends NdlaController {
    protected val applicationDescription = "API for accessing internal functionality in draft API"
    protected implicit override val jsonFormats: Formats = org.json4s.DefaultFormats

    post("/index") {
      implicit val ec: ExecutionContextExecutorService =
        ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

      val aggregateFuture = for {
        draftFuture <- Future(draftConceptIndexService.indexDocuments)
        publishedFuture <- Future(publishedConceptIndexService.indexDocuments)
      } yield (draftFuture, publishedFuture)

      Await.result(aggregateFuture, 10 minutes) match {
        case (Success(draftReindex), Success(publishedReindex)) =>
          val msg =
            s"""Completed indexing of ${draftReindex.totalIndexed} draft concepts in ${draftReindex.millisUsed} ms.
               |Completed indexing of ${publishedReindex.totalIndexed} published concepts in ${publishedReindex.millisUsed} ms.
               |""".stripMargin
          logger.info(msg)
          Ok(msg)
        case (Failure(ex), _) =>
          logger.error(s"Reindexing draft concepts failed with ${ex.getMessage}", ex)
          errorHandler(ex)
        case (_, Failure(ex)) =>
          logger.error(s"Reindexing published concepts failed with ${ex.getMessage}", ex)
          errorHandler(ex)
      }
    }

    delete("/index") {
      def pluralIndex(n: Int) = if (n == 1) "1 index" else s"$n indexes"
      draftConceptIndexService.findAllIndexes(ConceptApiProperties.DraftConceptSearchIndex) match {
        case Failure(ex) =>
          logger.error("Could not find indexes to delete.")
          errorHandler(ex)
        case Success(indexesToDelete) =>
          val deleted = indexesToDelete.map(index => draftConceptIndexService.deleteIndexWithName(Some(index)))
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

    post("/import/listing") {
      UserInfo.get match {
        case Some(user) if user.canWrite =>
          val start = System.currentTimeMillis
          val forceUpdate = booleanOrDefault("forceUpdate", default = false)
          importService.importListings(forceUpdate) match {
            case Success(result) =>
              if (result.numSuccessfullyImportedConcepts < result.totalAttemptedImportedConcepts) {
                InternalServerError(result)
              } else {
                Ok(result)
              }
            case Failure(ex) =>
              val errMsg =
                s"Import of listings failed after ${System.currentTimeMillis - start} ms with error: ${ex.getMessage}\n"
              logger.warn(errMsg, ex)
              InternalServerError(body = errMsg)
          }

        case _ => Unauthorized("You do not have access to perform this action")
      }
    }

    post("/import/concept") {
      UserInfo.get match {
        case Some(user) if user.canWrite =>
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
