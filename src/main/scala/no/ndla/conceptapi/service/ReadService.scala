/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import no.ndla.conceptapi.repository.ConceptRepository
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.api.NotFoundException

import scala.util.{Failure, Success, Try}

trait ReadService {
  this: ConceptRepository with ConceptRepository with ConverterService =>
  val readService: ReadService

  class ReadService {

    def conceptWithId(id: Long, language: String, fallback: Boolean): Try[api.Concept] =
      conceptRepository.withId(id) match {
        case Some(concept) =>
          converterService.toApiConcept(concept, language, fallback)
        case None =>
          Failure(NotFoundException(s"Concept with id $id was not found with language '$language' in database."))
      }

    def allSubjects(): Try[Set[String]] = {
      val subjectIds = conceptRepository.allSubjectIds
      if (subjectIds.size > 0) Success(subjectIds) else Failure(NotFoundException("Could not find any subjects"))
    }

  }
}
