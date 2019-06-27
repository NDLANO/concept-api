package no.ndla.conceptapi.service

import no.ndla.conceptapi.repository.ConceptRepository
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.api.NotFoundException

import scala.util.{Failure, Success, Try}

trait WriteService {
  this: ConceptRepository with ConverterService =>
  val writeService: WriteService

  class WriteService {

//    def publishConcept(id: Long): Try[domain.Concept] = {
//      conceptRepository.withId(id) match {
//        case Some(concept) =>
//          articleApiClient.updateConcept(id, converterService.toArticleApiConcept(concept)) match {
//            case Success(_)  => Success(concept)
//            case Failure(ex) => Failure(ex)
//          }
//        case None => Failure(NotFoundException(s"Article with id $id does not exist"))
//      }
//    }
//
//    def deleteConcept(id: Long): Try[api.ContentId] = {
//      conceptRepository
//        .delete(id)
//        //.flatMap(conceptIndexService.deleteDocument)
//        .map(api.ContentId)
//    }

    def newConcept(newConcept: api.NewConcept,
                   externalId: String): Try[api.Concept] = {
      for {
        concept <- converterService.toDomainConcept(newConcept)
        //_ <- importValidator.validate(concept)
        persistedConcept <- Try(
          conceptRepository.insertWithExternalId(concept, externalId))
        //_ <- conceptIndexService.indexDocument(concept)
      } yield
        converterService.toApiConcept(persistedConcept, newConcept.language)
    }
    private def updateConcept(
        toUpdate: domain.Concept,
        externalId: Option[String] = None): Try[domain.Concept] = {
      val updateFunc = externalId match {
        case None => conceptRepository.update _
        case Some(nid) =>
          (a: domain.Concept) =>
            conceptRepository.updateWithExternalId(a, nid)
      }

      for {
        //_ <- contentValidator.validate(toUpdate, allowUnknownLanguage = true)
        domainConcept <- updateFunc(toUpdate)
        //_ <- conceptIndexService.indexDocument(domainConcept)
      } yield domainConcept
    }

    def updateConcept(id: Long,
                      updatedConcept: api.UpdatedConcept,
                      externalId: Option[String]): Try[api.Concept] = {
      conceptRepository.withId(id) match {
        case Some(concept) =>
          val domainConcept =
            converterService.toDomainConcept(concept, updatedConcept)
          updateConcept(domainConcept, externalId)
            .map(x => converterService.toApiConcept(x, updatedConcept.language))
        case None if conceptRepository.exists(id) =>
          val concept = converterService.toDomainConcept(id, updatedConcept)
          updateConcept(concept, externalId)
            .map(concept =>
              converterService.toApiConcept(concept, updatedConcept.language))
        case None =>
          Failure(NotFoundException(s"Concept with id $id does not exist"))
      }
    }

  }
}
