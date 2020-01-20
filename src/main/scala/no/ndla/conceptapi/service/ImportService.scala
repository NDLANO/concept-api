/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.integration.{ArticleApiClient, DomainImageMeta, ImageApiClient, ListingApiClient}
import no.ndla.conceptapi.model.api.{ConceptImportResults, ImportException}
import no.ndla.conceptapi.model.api.listing.Cover
import no.ndla.conceptapi.repository.ConceptRepository
import no.ndla.conceptapi.model.domain
import cats._
import cats.data._
import cats.implicits._

import scala.util.{Failure, Success, Try}

trait ImportService {
  this: ConverterService
    with Clock
    with User
    with WriteService
    with ConceptRepository
    with ArticleApiClient
    with ListingApiClient
    with ImageApiClient =>
  val importService: ImportService

  class ImportService extends LazyLogging {

    private def getConceptMetaImages(image: DomainImageMeta): Seq[domain.ConceptMetaImage] = {
      image.alttexts.map(alt => domain.ConceptMetaImage(image.id.toString, alt.alttext, alt.language))
    }

    private def getSubjectIdFromTheme(theme: String) = {
      val themeMapping = Map(
        "verktoy" -> "urn:subject:11",
        "naturbruk" -> "urn:subject:13"
      )
      themeMapping.get(theme)
    }

    def convertListingToConcept(listing: Cover): Try[(domain.Concept, Long, Seq[String])] =
      listing.id match {
        case Some(coverId) =>
          val domainCoverImage = imageApiClient.getImage(listing.coverPhotoUrl)

          val titles = listing.title.map(t => domain.ConceptTitle(t.title, t.language))
          val contents = listing.description.map(c => domain.ConceptContent(c.description, c.language))
          val metaImages = domainCoverImage.map(getConceptMetaImages).toOption.toSeq.flatten
          val subjectIds = getSubjectIdFromTheme(listing.theme).toSet

          val metaImageWarning = if (metaImages.isEmpty) {
            val msg =
              s"Imported listing cover with id ${listing.id.getOrElse(-1)} does not have any meta images..."
            logger.warn(msg)
            msg.some
          } else { None }

          val taxonomyWarning = if (subjectIds.isEmpty) {
            val msg =
              s"Imported listing cover with id ${listing.id.getOrElse(-1)}, could not be connected to taxonomy..."
            logger.warn(msg)
            msg.some
          } else { None }

          val tags = listing.labels.flatMap(languageLabels => {
            val filteredTags = languageLabels.labels.filter(_.`type`.getOrElse("category") == "category")
            filteredTags.map(t => domain.ConceptTags(t.labels, languageLabels.language))
          })

          Success(
            (
              domain.Concept(
                id = None,
                title = titles,
                content = contents,
                copyright = None,
                source = None,
                created = listing.updated,
                updated = listing.updated,
                metaImage = metaImages,
                tags = tags,
                subjectIds = subjectIds,
                articleId = None
              ),
              coverId,
              metaImageWarning.toSeq ++ taxonomyWarning.toSeq
            ))
        case None =>
          val msg = "Could not import cover because it was missing an id."
          logger.error(msg)
          Failure(ImportException(msg))
      }

    def importListings(forceUpdate: Boolean): Try[ConceptImportResults] = {
      val start = System.currentTimeMillis()
      val pageStream = listingApiClient.getChunks
      pageStream
        .map(page => page.map(successfulPage => convertAndInsertSuccessfulPage(successfulPage, forceUpdate)))
        .toList
        .sequence
        .map(done => handleFinishedImport(start, done))
    }

    private def convertAndInsertSuccessfulPage(successfulPage: Seq[Cover], forceUpdate: Boolean) = {
      val convertedConcepts = successfulPage.toList.map(convertListingToConcept)
      val inserted = convertedConcepts.sequence.map(converted => {
        val conceptsWithListingId = converted.map { case (concept, listingId, _) => (concept, listingId) }
        writeService.insertListingImportedConcepts(conceptsWithListingId, forceUpdate)
      })

      val convertWarnings = convertedConcepts.collect { case Success((_, _, warnings)) => warnings }.flatten
      val convertFailWarnings = convertedConcepts.collect { case Failure(ex) => ex.getMessage }
      val insertWarnings = inserted.map(_.collect { case Failure(ex) => ex.getMessage }).getOrElse(Seq.empty)
      val allWarnings = convertFailWarnings ++ convertWarnings ++ insertWarnings

      val numSuccessfullySaved = inserted.map(_.count(_.isSuccess)).getOrElse(0)
      (numSuccessfullySaved, successfulPage.size, allWarnings)
    }

    def importConcepts(forceUpdate: Boolean): Try[ConceptImportResults] = {
      val start = System.currentTimeMillis()
      val pageStream = articleApiClient.getChunks
      pageStream
        .map(page => {
          page.map(successfulPage => {
            val saved = writeService.saveImportedConcepts(successfulPage, forceUpdate)
            val numSuccessfullySaved = saved.count(_.isSuccess)
            val warnings = saved.collect { case Failure(ex) => ex.getMessage }
            (numSuccessfullySaved, successfulPage.size, warnings)
          })
        })
        .toList
        .sequence
        .map(done => handleFinishedImport(start, done))
    }

    private def handleFinishedImport(startTime: Long, successfulPages: List[(Int, Int, Seq[String])]) = {
      conceptRepository.updateIdCounterToHighestId()
      val (totalSaved, totalAttempted, allWarnings) = successfulPages.foldLeft((0, 0, Seq.empty[String])) {
        case ((tmpTotalSaved, tmpTotalAttempted, tmpWarnings), (pageSaved, pageAttempted, pageWarnings)) =>
          (tmpTotalSaved + pageSaved, tmpTotalAttempted + pageAttempted, tmpWarnings ++ pageWarnings)
      }

      val usedTime = System.currentTimeMillis() - startTime
      logger.info(s"Successfully saved $totalSaved out of $totalAttempted attempted imported concepts in $usedTime ms.")
      ConceptImportResults(totalSaved, totalAttempted, allWarnings)
    }

  }

}
