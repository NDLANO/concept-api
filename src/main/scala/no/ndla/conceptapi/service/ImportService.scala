/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import com.typesafe.scalalogging.LazyLogging
import io.lemonlabs.uri.{Url, UrlPath}
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.model.api.ConceptImportResults
import no.ndla.mapping.License.getLicense
import no.ndla.mapping.LicenseDefinition
import scalaj.http.Http
import no.ndla.conceptapi.model.domain

import scala.util.{Failure, Success, Try}

trait ImportService {
  this: ConverterService with Clock with User =>
  val importService: ImportService

  class ImportService extends LazyLogging {

    def importConcepts(): Try[ConceptImportResults] = {
      val start = System.currentTimeMillis()

      val pageStream: Stream[Try[List[domain.Concept]]] = ???

      val done = pageStream
        .map(page => {
          page.map(successfulPage => {
            val numSuccessfullySaved = saveConcepts(successfulPage)
            (numSuccessfullySaved, successfulPage.size)
          })
        })
        .toList

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

    def saveConcepts(concepts: List[domain.Concept]): Int =
      ??? //lagre begrep i databasen, håndtere at begreper kan ha samme id, kanskje noe i writeService som fikser lagring til database??

    /*

    private[service] def toDomainImage(imageMeta: MainImageImport,
                                       rawImage: domain.Image): domain.ImageMetaInformation = {
      val (translationTitles, translationAltTexts, translationCaptions) = toDomainTranslationFields(imageMeta)
      val mainLanguage = Option(imageMeta.mainImage.language).filter(_.nonEmpty).getOrElse(Language.UnknownLanguage)
      val titles = translationTitles :+ domain.ImageTitle(imageMeta.mainImage.title, mainLanguage)
      val altTexts = translationAltTexts ++ imageMeta.mainImage.alttext
        .map(alt => Seq(domain.ImageAltText(alt, mainLanguage)))
        .getOrElse(Seq.empty)
      val captions = translationCaptions ++ imageMeta.mainImage.caption
        .map(cap => Seq(domain.ImageCaption(cap, mainLanguage)))
        .getOrElse(Seq.empty)
      val tags = getTags(imageMeta.mainImage.nid +: imageMeta.translations.map(_.nid),
                         Language.findSupportedLanguages(titles, altTexts, captions))

      domain.ImageMetaInformation(
        None,
        titles,
        altTexts,
        rawImage.fileName,
        rawImage.size,
        rawImage.contentType,
        toDomainCopyright(imageMeta),
        tags,
        captions,
        authUser.userOrClientid(),
        clock.now()
      )
    }
     */
    /* private def persistMetadata(image: domain.ImageMetaInformation,
                                externalImageId: String): Try[ImageMetaInformation] = {
      imageRepository.withExternalId(externalImageId) match {
        case Some(dbMeta) => Try(imageRepository.update(image.copy(id = dbMeta.id), dbMeta.id.get))
        case None         => Try(imageRepository.insertWithExternalId(image, externalImageId))
      }
    }

    private[service] def oldToNewLicenseKey(license: String): Option[LicenseDefinition] = {
      val licenses = Map(
        "by" -> "CC-BY-4.0",
        "by-sa" -> "CC-BY-SA-4.0",
        "by-nc" -> "CC-BY-NC-4.0",
        "by-nd" -> "CC-BY-ND-4.0",
        "by-nc-sa" -> "CC-BY-NC-SA-4.0",
        "by-nc-nd" -> "CC-BY-NC-ND-4.0",
        "by-3.0" -> "CC-BY-4.0",
        "by-sa-3.0" -> "CC-BY-SA-4.0",
        "by-nc-3.0" -> "CC-BY-NC-4.0",
        "by-nd-3.0" -> "CC-BY-ND-4.0",
        "by-nc-sa-3.0" -> "CC-BY-NC-SA-4.0",
        "by-nc-nd-3.0" -> "CC-BY-NC-ND-4.0",
        "copyrighted" -> "COPYRIGHTED",
        "cc0" -> "CC0-1.0",
        "pd" -> "PD",
        "nolaw" -> "CC0-1.0",
        "noc" -> "PD"
      )
      val newLicense = getLicense(licenses.getOrElse(license, license))
      if (newLicense.isEmpty) {
        throw new ImportException(s"License $license is not supported.")
      }
      newLicense
    }*/
    /*private[service] def toDomainCopyright(imageMeta: MainImageImport): domain.Copyright = {
      val domainLicense = imageMeta.license.flatMap(oldToNewLicenseKey).map(_.license.toString).getOrElse("COPYRIGHTED")

      val creators =
        imageMeta.authors.filter(a => oldCreatorTypes.contains(a.typeAuthor.toLowerCase)).map(toNewAuthorType)
      val processors =
        imageMeta.authors.filter(a => oldProcessorTypes.contains(a.typeAuthor.toLowerCase)).map(toNewAuthorType)
      val rightsholders =
        imageMeta.authors.filter(a => oldRightsholderTypes.contains(a.typeAuthor.toLowerCase)).map(toNewAuthorType)

      domain.Copyright(domainLicense,
                       imageMeta.origin.getOrElse(""),
                       creators,
                       processors,
                       rightsholders,
                       agreementId = None,
                       validFrom = None,
                       validTo = None)
    }*/

    /*  private def toDomainTranslationFields(
        imageMeta: MainImageImport): (Seq[domain.ImageTitle], Seq[domain.ImageAltText], Seq[domain.ImageCaption]) = {
      imageMeta.translations
        .map(tr => {
          val language = tr.language
          (domain.ImageTitle(tr.title, language),
           domain.ImageAltText(tr.alttext.getOrElse(""), language),
           domain.ImageCaption(tr.caption.getOrElse(""), language))
        })
        .unzip3
    }*/

  }

}
