/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.integration.{ArticleApiClient, ImageApiClient, ListingApiClient}
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

    private def getMetaImageInfo(imageId: Long, supportedLanguages: Set[String]) =
      supportedLanguages
        .map(lang => {
          val altText = imageApiClient.getImageAltText(imageId, lang) match {
            case Failure(exception) =>
              logger.error("Something went wrong when fetching altText for meta image", exception)
              ""
            case Success(alt) => alt.alttext.alttext
          }
          domain.ConceptMetaImage(imageId.toString, altText, lang)
        })
        .toSeq

    def convertListingToConcept(listing: Cover): Try[(domain.Concept, Long)] =
      listing.id match {
        case Some(coverId) =>
          val coverPhotoId = imageApiClient.getImageId(listing.coverPhotoUrl)

          val titles = listing.title.map(t => domain.ConceptTitle(t.title, t.language))
          val contents = listing.description.map(c => domain.ConceptContent(c.description, c.language))
          val tags = listing.labels.flatMap(languageLabels => {
            val filteredTags = languageLabels.labels.filter(_.`type`.getOrElse("category") == "category")
            filteredTags.map(t => domain.ConceptTags(t.labels, languageLabels.language))
          })

          coverPhotoId.map(metaImageId => {
            val supportedLanguages = (contents union titles union tags).map(_.language).toSet
            val metaImages = getMetaImageInfo(metaImageId, supportedLanguages)
            (
              domain.Concept(
                id = None,
                title = titles,
                content = contents,
                copyright = None,
                created = listing.updated,
                updated = listing.updated,
                metaImage = metaImages,
                tags = tags,
                subjectIds = Set.empty
              ),
              coverId
            )
          })
        case None =>
          val msg = "Could not import cover because it was missing an id."
          logger.error(msg)
          Failure(ImportException(msg))
      }

    def importListings(forceUpdate: Boolean): Try[ConceptImportResults] = {
      val start = System.currentTimeMillis()
      val pageStream = listingApiClient.getChunks
      pageStream
        .map(page => {
          page.map(successfulPage => {
            val imported = successfulPage.toList
              .traverse(convertListingToConcept)
              .map(converted => {
                writeService.insertListingImportedConcepts(converted, forceUpdate)
              })
            val numSuccessfullySaved = imported.map(_.count(_.isSuccess)).getOrElse(0)
            (numSuccessfullySaved, successfulPage.size)
          })
        })
        .toList
        .sequence
        .map(done => handleFinishedImport(start, done))
    }

    def importConcepts(forceUpdate: Boolean): Try[ConceptImportResults] = {
      val start = System.currentTimeMillis()
      val pageStream = articleApiClient.getChunks
      pageStream
        .map(page => {
          page.map(successfulPage => {
            val saved = writeService.saveImportedConcepts(successfulPage, forceUpdate)
            val numSuccessfullySaved = saved.count(_.isSuccess)
            (numSuccessfullySaved, successfulPage.size)
          })
        })
        .toList
        .sequence
        .map(done => handleFinishedImport(start, done))
    }

    private def handleFinishedImport(startTime: Long, successfulPages: List[(Int, Int)]) = {
      conceptRepository.updateIdCounterToHighestId()
      val (totalSaved, totalAttempted) = successfulPages.foldLeft((0, 0)) {
        case ((tmpTotalSaved, tmpTotalAttempted), (pageSaved, pageAttempted)) =>
          (tmpTotalSaved + pageSaved, tmpTotalAttempted + pageAttempted)
      }

      val usedTime = System.currentTimeMillis() - startTime
      logger.info(s"Successfully saved $totalSaved out of $totalAttempted attempted imported concepts in $usedTime ms.")
      ConceptImportResults(totalSaved, totalAttempted)
    }

  }

}
