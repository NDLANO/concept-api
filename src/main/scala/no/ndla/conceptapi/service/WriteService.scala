/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import no.ndla.conceptapi.repository.ConceptRepository
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.api.NotFoundException
import no.ndla.conceptapi.service.search.ConceptIndexService
import no.ndla.conceptapi.validation._

import scala.util.{Failure, Try}

trait WriteService {
  this: ConceptRepository with ConverterService with ContentValidator with ConceptIndexService =>
  val writeService: WriteService

  class WriteService {

    def newConcept(newConcept: api.NewConcept): Try[api.Concept] = {
      for {
        concept <- converterService.toDomainConcept(newConcept)
        _ <- contentValidator.validateConcept(concept, allowUnknownLanguage = false)
        persistedConcept <- Try(conceptRepository.insert(concept))
        _ <- conceptIndexService.indexDocument(persistedConcept)
        apiC <- converterService.toApiConcept(persistedConcept, newConcept.language, fallback = true)
      } yield apiC
    }

    private def updateConcept(toUpdate: domain.Concept): Try[domain.Concept] = {
      for {
        _ <- contentValidator.validateConcept(toUpdate, allowUnknownLanguage = true)
        domainConcept <- conceptRepository.update(toUpdate)
        _ <- conceptIndexService.indexDocument(domainConcept)
      } yield domainConcept
    }

    def updateConcept(id: Long, updatedConcept: api.UpdatedConcept): Try[api.Concept] = {
      conceptRepository.withId(id) match {
        case Some(concept) =>
          val domainConcept =
            converterService.toDomainConcept(concept, updatedConcept)
          updateConcept(domainConcept).flatMap(x =>
            converterService.toApiConcept(x, updatedConcept.language, fallback = true))
        case None if conceptRepository.exists(id) =>
          val concept = converterService.toDomainConcept(id, updatedConcept)
          updateConcept(concept)
            .flatMap(concept => converterService.toApiConcept(concept, updatedConcept.language, fallback = true))
        case None =>
          Failure(NotFoundException(s"Concept with id $id does not exist"))
      }
    }

  }
}
