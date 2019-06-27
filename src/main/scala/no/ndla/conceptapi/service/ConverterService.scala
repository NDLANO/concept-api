package no.ndla.conceptapi.service

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.repository.ConceptRepository
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.domain.Language._
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.domain.LanguageField
import no.ndla.mapping.License.getLicense
import org.joda.time.format.ISODateTimeFormat

import scala.util.control.Exception.allCatch
import scala.util.{Failure, Success, Try}

trait ConverterService {
  this: Clock with ConceptRepository =>
  val converterService: ConverterService

  class ConverterService extends LazyLogging {

    def toApiConcept(concept: domain.Concept, language: String): api.Concept = {
      val title = findByLanguageOrBestEffort(concept.title, language)
        .map(toApiConceptTitle)
        .getOrElse(api.ConceptTitle("", DefaultLanguage))
      val content = findByLanguageOrBestEffort(concept.content, language)
        .map(toApiConceptContent)
        .getOrElse(api.ConceptContent("", DefaultLanguage))

      api.Concept(
        concept.id.get,
        Some(title),
        Some(content),
        concept.copyright.map(toApiCopyright),
        concept.created,
        concept.updated,
        concept.supportedLanguages
      )
    }

    def toApiCopyright(copyright: domain.Copyright): api.Copyright = {
      api.Copyright(
        copyright.license.map(toApiLicense),
        copyright.origin,
        copyright.creators.map(toApiAuthor),
        copyright.processors.map(toApiAuthor),
        copyright.rightsholders.map(toApiAuthor),
        copyright.agreementId,
        copyright.validFrom,
        copyright.validTo
      )
    }

    def toApiLicense(shortLicense: String): api.License = {
      getLicense(shortLicense)
        .map(l => api.License(l.license.toString, Option(l.description), l.url))
        .getOrElse(api.License("unknown", None, None))
    }

    def toApiAuthor(author: domain.Author): api.Author =
      api.Author(author.`type`, author.name)

    def toApiConceptTitle(title: domain.ConceptTitle): api.ConceptTitle =
      api.ConceptTitle(title.title, title.language)

    def toApiConceptContent(title: domain.ConceptContent): api.ConceptContent =
      api.ConceptContent(title.content, title.language)

    def toDomainConcept(concept: api.NewConcept): Try[domain.Concept] = {
      Success(
        domain.Concept(
          None,
          Seq(domain.ConceptTitle(concept.title, concept.language)),
          concept.content
            .map(content =>
              Seq(domain.ConceptContent(content, concept.language)))
            .getOrElse(Seq.empty),
          concept.copyright.map(toDomainCopyright),
          clock.now(),
          clock.now()
        ))
    }

    def toDomainConcept(toMergeInto: domain.Concept,
                        updateConcept: api.UpdatedConcept): domain.Concept = {
      val domainTitle = updateConcept.title
        .map(t => domain.ConceptTitle(t, updateConcept.language))
        .toSeq
      val domainContent = updateConcept.content
        .map(c => domain.ConceptContent(c, updateConcept.language))
        .toSeq

      toMergeInto.copy(
        title = mergeLanguageFields(toMergeInto.title, domainTitle),
        content = mergeLanguageFields(toMergeInto.content, domainContent),
        copyright = updateConcept.copyright
          .map(toDomainCopyright)
          .orElse(toMergeInto.copyright),
        created = toMergeInto.created,
        updated = clock.now()
      )
    }

    def toDomainConcept(id: Long,
                        article: api.UpdatedConcept): domain.Concept = {
      val lang = article.language

      domain.Concept(
        id = Some(id),
        title = article.title.map(t => domain.ConceptTitle(t, lang)).toSeq,
        content = article.content.map(c => domain.ConceptContent(c, lang)).toSeq,
        copyright = article.copyright.map(toDomainCopyright),
        created = clock.now(),
        updated = clock.now()
      )
    }

    def toDomainCopyright(
        newCopyright: api.NewAgreementCopyright): domain.Copyright = {
      val parser = ISODateTimeFormat.dateOptionalTimeParser()
      val validFrom = newCopyright.validFrom.flatMap(date =>
        allCatch.opt(parser.parseDateTime(date).toDate))
      val validTo = newCopyright.validTo.flatMap(date =>
        allCatch.opt(parser.parseDateTime(date).toDate))

      val apiCopyright = api.Copyright(
        newCopyright.license,
        newCopyright.origin,
        newCopyright.creators,
        newCopyright.processors,
        newCopyright.rightsholders,
        newCopyright.agreementId,
        validFrom,
        validTo
      )
      toDomainCopyright(apiCopyright)
    }

    def toDomainCopyright(copyright: api.Copyright): domain.Copyright = {
      domain.Copyright(
        copyright.license.map(_.license),
        copyright.origin,
        copyright.creators.map(toDomainAuthor),
        copyright.processors.map(toDomainAuthor),
        copyright.rightsholders.map(toDomainAuthor),
        copyright.agreementId,
        copyright.validFrom,
        copyright.validTo
      )
    }

    def toDomainAuthor(author: api.Author): domain.Author =
      domain.Author(author.`type`, author.name)

    private[service] def mergeLanguageFields[A <: LanguageField](
        existing: Seq[A],
        updated: Seq[A]): Seq[A] = {
      val toKeep = existing.filterNot(item =>
        updated.map(_.language).contains(item.language))
      (toKeep ++ updated).filterNot(_.isEmpty)
    }

  }

}
