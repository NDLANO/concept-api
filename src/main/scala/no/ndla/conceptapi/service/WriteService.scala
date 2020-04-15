/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.auth.UserInfo
import no.ndla.conceptapi.repository.{DraftConceptRepository, PublishedConceptRepository}
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.domain.ConceptStatus._
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.api.{ConceptExistsAlreadyException, NotFoundException}
import no.ndla.conceptapi.service.search.DraftConceptIndexService
import no.ndla.conceptapi.validation._

import scala.util.{Failure, Success, Try}

trait WriteService {
  this: DraftConceptRepository
    with PublishedConceptRepository
    with ConverterService
    with ContentValidator
    with DraftConceptIndexService
    with LazyLogging =>
  val writeService: WriteService

  class WriteService {

    def insertListingImportedConcepts(conceptsWithListingId: Seq[(domain.Concept, Long)],
                                      forceUpdate: Boolean): Seq[Try[domain.Concept]] = {
      conceptsWithListingId.map {
        case (concept, listingId) =>
          val existing = draftConceptRepository.withListingId(listingId).nonEmpty
          if (existing && !forceUpdate) {
            logger.warn(
              s"Concept with listing_id of $listingId already exists, and forceUpdate was not 'true', skipping...")
            Failure(ConceptExistsAlreadyException(s"the concept already exists with listing_id of $listingId."))
          } else if (existing && forceUpdate) {
            draftConceptRepository.updateWithListingId(concept, listingId)
          } else {
            Success(draftConceptRepository.insertwithListingId(concept, listingId))
          }
      }
    }

    def saveImportedConcepts(concepts: Seq[domain.Concept], forceUpdate: Boolean): Seq[Try[domain.Concept]] = {
      concepts.map(concept => {
        concept.id match {
          case Some(id) if draftConceptRepository.exists(id) =>
            if (forceUpdate) {
              val existing = draftConceptRepository.withId(id)
              updateConcept(concept) match {
                case Failure(ex) =>
                  logger.error(s"Could not update concept with id '${concept.id.getOrElse(-1)}' when importing.")
                  Failure(ex)
                case Success(c) =>
                  logger.info(s"Updated concept with id '${c.id.getOrElse(-1)}' successfully during import.")
                  Success(c)
              }
            } else {
              Failure(ConceptExistsAlreadyException("The concept already exists."))
            }
          case None => draftConceptRepository.insertWithId(concept)
        }
      })
    }

    def newConcept(newConcept: api.NewConcept): Try[api.Concept] = {
      for {
        concept <- converterService.toDomainConcept(newConcept)
        _ <- contentValidator.validateConcept(concept, allowUnknownLanguage = false)
        persistedConcept <- Try(draftConceptRepository.insert(concept))
        _ <- draftConceptIndexService.indexDocument(persistedConcept)
        apiC <- converterService.toApiConcept(persistedConcept, newConcept.language, fallback = true)
      } yield apiC
    }

    private def shouldUpdateStatus(existing: domain.Concept, changed: domain.Concept): Boolean = {
      // Function that sets values we don't want to include when comparing concepts to check if we should update status
      val withComparableValues =
        (concept: domain.Concept) =>
          concept.copy(
            revision = None,
            created = new Date(0),
            updated = new Date(0)
        )
      withComparableValues(existing) != withComparableValues(changed)
    }

    private def updateStatusIfNeeded(existing: domain.Concept, changed: domain.Concept, user: UserInfo) = {
      if (!shouldUpdateStatus(existing, changed)) {
        Success(changed)
      } else {
        val oldStatus = existing.status.current
        val newStatus = if (oldStatus == PUBLISHED) DRAFT else oldStatus

        converterService
          .updateStatus(newStatus, changed, user)
          .attempt
          .unsafeRunSync()
          .toTry
          .flatten
      }
    }

    private def updateConcept(toUpdate: domain.Concept): Try[domain.Concept] = {
      for {
        _ <- contentValidator.validateConcept(toUpdate, allowUnknownLanguage = true)
        domainConcept <- draftConceptRepository.update(toUpdate)
        _ <- draftConceptIndexService.indexDocument(domainConcept)
      } yield domainConcept
    }

    def updateConcept(id: Long, updatedConcept: api.UpdatedConcept, userInfo: UserInfo): Try[api.Concept] = {
      draftConceptRepository.withId(id) match {
        case Some(existingConcept) =>
          val domainConcept = converterService.toDomainConcept(existingConcept, updatedConcept)

          for {
            withStatus <- updateStatusIfNeeded(existingConcept, domainConcept, userInfo)
            updated <- updateConcept(withStatus)
            converted <- converterService.toApiConcept(updated, updatedConcept.language, fallback = true)
          } yield converted

        case None if draftConceptRepository.exists(id) =>
          val concept = converterService.toDomainConcept(id, updatedConcept)
          for {
            updated <- updateConcept(concept)
            converted <- converterService.toApiConcept(updated, updatedConcept.language, fallback = true)
          } yield converted
        case None =>
          Failure(NotFoundException(s"Concept with id $id does not exist"))
      }
    }

    def deleteLanguage(id: Long, language: String, userInfo: UserInfo): Try[api.Concept] = {
      draftConceptRepository.withId(id) match {
        case Some(existingConcept) =>
          existingConcept.title.size match {
            case 1 => Failure(api.OperationNotAllowedException("Only one language left"))
            case _ =>
              val title = existingConcept.title.filter(_.language != language)
              val content = existingConcept.content.filter(_.language != language)
              val newConcept = existingConcept.copy(
                title = title,
                content = content,
              )

              for {
                withStatus <- updateStatusIfNeeded(existingConcept, newConcept, userInfo)
                updated <- updateConcept(withStatus)
                converted <- converterService.toApiConcept(updated, domain.Language.AllLanguages, fallback = false)
              } yield converted
          }
        case None => Failure(NotFoundException("Concept does not exist"))
      }

    }

    def publishConcept(concept: domain.Concept): Try[domain.Concept] = {
      publishedConceptRepository.insertOrUpdate(concept)
    }
  }
}
