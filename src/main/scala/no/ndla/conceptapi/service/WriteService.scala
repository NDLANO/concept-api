/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.repository.ConceptRepository
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.api.{ConceptExistsAlready, NotFoundException}
import no.ndla.conceptapi.service.search.ConceptIndexService
import no.ndla.conceptapi.validation._

import scala.util.{Failure, Success, Try}

trait WriteService {
  this: ConceptRepository with ConverterService with ContentValidator with ConceptIndexService with LazyLogging =>
  val writeService: WriteService

  class WriteService {

    def saveImportedConcepts(concepts: Seq[domain.Concept], forceUpdate: Boolean): Seq[Try[domain.Concept]] = {
      concepts.map(concept => {
        if (concept.id.exists(conceptRepository.exists)) {
          if (forceUpdate) {
            updateConcept(concept) match {
              case Failure(ex) =>
                logger.error(s"Could not update concept with id '${concept.id.getOrElse(-1)}' when importing.")
                Failure(ex)
              case Success(c) =>
                logger.info(s"Updated concept with id '${c.id.getOrElse(-1)}' successfully during import.")
                Success(c)
            }
          } else {
            Failure(ConceptExistsAlready("The concept already exists."))
          }
        } else {
          conceptRepository.insertWithId(concept)
        }
      })
    }

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

    def deleteLanguage(id: Long, language: String): Try[api.Concept] = {
      conceptRepository.withId(id) match {
        case Some(concept) =>
          concept.title.size match {
            case 1 => Failure(api.OperationNotAllowedException("Only one language left"))
            case _ =>
              val title = concept.title.filter(_.language != language)
              val content = concept.content.filter(_.language != language)
              val newConcept = concept.copy(
                title = title,
                content = content,
              )
              conceptRepository
                .update(newConcept)
                .flatMap(
                  converterService.toApiConcept(_, domain.Language.AllLanguages, false)
                )
          }
        case None => Failure(NotFoundException("Concept does not exist"))
      }

    }

  }
}
