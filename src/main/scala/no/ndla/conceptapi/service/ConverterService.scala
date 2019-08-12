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
import no.ndla.conceptapi.model.domain.Language._
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.api.NotFoundException
import no.ndla.conceptapi.model.domain.LanguageField
import no.ndla.mapping.License.getLicense
import no.ndla.conceptapi.ConceptApiProperties._

import scala.util.{Failure, Success, Try}

trait ConverterService {
  this: Clock with ConceptRepository =>
  val converterService: ConverterService

  class ConverterService extends LazyLogging {

    def toApiConcept(concept: domain.Concept, language: String, fallback: Boolean): Try[api.Concept] = {
      val isLanguageNeutral = concept.supportedLanguages.contains(UnknownLanguage) && concept.supportedLanguages.size == 1
      if (concept.supportedLanguages.contains(language) || fallback || isLanguageNeutral || language == AllLanguages) {
        val title = findByLanguageOrBestEffort(concept.title, language)
          .map(toApiConceptTitle)
          .getOrElse(api.ConceptTitle("", UnknownLanguage))
        val content = findByLanguageOrBestEffort(concept.content, language)
          .map(toApiConceptContent)
          .getOrElse(api.ConceptContent("", UnknownLanguage))
        val metaImage = findByLanguageOrBestEffort(concept.metaImage, language)
          .map(toApiMetaImage)
          .getOrElse(api.ConceptMetaImage("", "", UnknownLanguage))

        val tags = findByLanguageOrBestEffort(concept.tags, language)
          .map(toApiTags)

        Success(
          api.Concept(
            concept.id.get,
            Some(title),
            Some(content),
            concept.copyright.map(toApiCopyright),
            Some(metaImage),
            tags,
            concept.subjectIds,
            concept.created,
            concept.updated,
            concept.supportedLanguages,
          )
        )
      } else {
        Failure(
          NotFoundException(s"The concept with id ${concept.id.getOrElse(-1)} and language '$language' was not found.",
                            concept.supportedLanguages.toSeq))
      }
    }

    private def toApiTags(tags: domain.ConceptTags) = {
      api.ConceptTags(
        tags.tags,
        tags.language
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

    def toApiMetaImage(metaImage: domain.ConceptMetaImage): api.ConceptMetaImage =
      api.ConceptMetaImage(s"${externalApiUrls("raw-image")}/${metaImage.imageId}",
                           metaImage.altText,
                           metaImage.language)

    def toDomainConcept(concept: api.NewConcept): Try[domain.Concept] = {
      Success(
        domain.Concept(
          id = None,
          title = Seq(domain.ConceptTitle(concept.title, concept.language)),
          content = concept.content
            .map(content => Seq(domain.ConceptContent(content, concept.language)))
            .getOrElse(Seq.empty),
          copyright = concept.copyright.map(toDomainCopyright),
          created = clock.now(),
          updated = clock.now(),
          metaImage = concept.metaImage.map(m => domain.ConceptMetaImage(m.id, m.alt, concept.language)).toSeq,
          tags = concept.tags.map(t => toDomainTags(t, concept.language)).getOrElse(Seq.empty),
          subjectIds = concept.subjectIds.getOrElse(Seq.empty).toSet
        ))
    }

    private def toDomainTags(tags: Seq[String], language: String): Seq[domain.ConceptTags] =
      if (tags.isEmpty) Seq.empty else Seq(domain.ConceptTags(tags, language))

    def toDomainConcept(toMergeInto: domain.Concept, updateConcept: api.UpdatedConcept): domain.Concept = {
      val domainTitle = updateConcept.title
        .map(t => domain.ConceptTitle(t, updateConcept.language))
        .toSeq
      val domainContent = updateConcept.content
        .map(c => domain.ConceptContent(c, updateConcept.language))
        .toSeq
      val domainMetaImage = updateConcept.metaImage
        .map(m => domain.ConceptMetaImage(m.id, m.alt, updateConcept.language))
        .toSeq

      val domainTags = updateConcept.tags.map(t => domain.ConceptTags(t, updateConcept.language)).toSeq

      toMergeInto.copy(
        title = mergeLanguageFields(toMergeInto.title, domainTitle),
        content = mergeLanguageFields(toMergeInto.content, domainContent),
        copyright = updateConcept.copyright
          .map(toDomainCopyright)
          .orElse(toMergeInto.copyright),
        created = toMergeInto.created,
        updated = clock.now(),
        metaImage = mergeLanguageFields(toMergeInto.metaImage, domainMetaImage),
        tags = mergeLanguageFields(toMergeInto.tags, domainTags),
        subjectIds = updateConcept.subjectIds.map(_.toSet).getOrElse(toMergeInto.subjectIds)
      )
    }

    def toDomainConcept(id: Long, concept: api.UpdatedConcept): domain.Concept = {
      val lang = concept.language

      domain.Concept(
        id = Some(id),
        title = concept.title.map(t => domain.ConceptTitle(t, lang)).toSeq,
        content = concept.content.map(c => domain.ConceptContent(c, lang)).toSeq,
        copyright = concept.copyright.map(toDomainCopyright),
        created = clock.now(),
        updated = clock.now(),
        metaImage = concept.metaImage.map(m => domain.ConceptMetaImage(m.id, m.alt, lang)).toSeq,
        tags = concept.tags.map(t => toDomainTags(t, concept.language)).getOrElse(Seq.empty),
        subjectIds = concept.subjectIds.getOrElse(Seq.empty).toSet
      )
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

    private[service] def mergeLanguageFields[A <: LanguageField](existing: Seq[A], updated: Seq[A]): Seq[A] = {
      val toKeep = existing.filterNot(item => updated.map(_.language).contains(item.language))
      (toKeep ++ updated).filterNot(_.isEmpty)
    }

  }

}
