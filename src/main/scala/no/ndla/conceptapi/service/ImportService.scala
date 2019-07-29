/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.integration.ArticleApiClient
import no.ndla.conceptapi.model.api.ConceptImportResults
import no.ndla.conceptapi.repository.ConceptRepository

import scala.util.{Failure, Success, Try}

trait ImportService {
  this: ConverterService with Clock with User with WriteService with ConceptRepository with ArticleApiClient =>
  val importService: ImportService

  class ImportService extends LazyLogging {

    def importConcepts(forceUpdate: Boolean): Try[ConceptImportResults] = {
      val start = System.currentTimeMillis()

      val pageStream = articleApiClient.getChunks

      val done = pageStream
        .map(page => {
          page.map(successfulPage => {
            val saved = writeService.saveImportedConcepts(successfulPage, forceUpdate)
            val numSuccessfullySaved = saved.count(_.isSuccess)
            (numSuccessfullySaved, successfulPage.size)
          })
        })
        .toList

      conceptRepository.updateIdCounterToHighestId()

      done.collect { case Failure(ex) => Failure(ex) } match {
        case Nil =>
          val successfulPages = done.collect {
            case Success((numSuccessfullySaved, pageSize)) => (numSuccessfullySaved, pageSize)
          }

          val (totalSaved, totalAttempted) = successfulPages.foldLeft((0, 0)) {
            case ((tmpTotalSaved, tmpTotalAttempted), (pageSaved, pageAttempted)) =>
              (tmpTotalSaved + pageSaved, tmpTotalAttempted + pageAttempted)
          }

          logger.info(s"Successfully saved $totalSaved out of $totalAttempted attempted imported concepts in ${System
            .currentTimeMillis() - start}ms.")
          Success(ConceptImportResults(totalSaved, totalAttempted))
        case fails => fails.head
      }

    }

  }

}
