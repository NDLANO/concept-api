/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import java.util.Date

import no.ndla.conceptapi.integration.{DomainImageMeta, ImageAltText}
import no.ndla.conceptapi.model.api.{ConceptExistsAlreadyException, Copyright, NotFoundException, UpdatedConcept}
import no.ndla.conceptapi.model.{api, domain}
import no.ndla.conceptapi.{TestData, TestEnvironment, UnitSuite}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.invocation.InvocationOnMock
import scalikejdbc.DBSession

import scala.util.{Failure, Success, Try}

class ImportServiceTest extends UnitSuite with TestEnvironment {

  val service = new ImportService

  val titles = Seq(api.listing.CoverTitle("Tittel da", "nb"), api.listing.CoverTitle("Tittel då", "nn"))

  val descriptions =
    Seq(api.listing.CoverDescription("Beskrivelse da", "nb"), api.listing.CoverDescription("Tittel då", "nn"))

  val labelnb = Seq(api.listing.CoverLabel(Some("category"), Seq("hei", "for", "noen", "fine", "labels")))
  val labelen = Seq(api.listing.CoverLabel(Some("category"), Seq("hello", "you", "have", "nice", "labels")))
  val labels = Seq(api.listing.CoverLanguageLabels(labelnb, "nb"), api.listing.CoverLanguageLabels(labelen, "en"))

  val domainCover = api.listing.Cover(
    id = Some(1),
    revision = None,
    oldNodeId = Some(1111),
    coverPhotoUrl = "https://test.test/image-api/raw/id/1234",
    title = titles,
    description = descriptions,
    labels = labels,
    articleApiId = 15,
    updatedBy = "somefancyclientid",
    updated = TestData.today,
    theme = "verktoy"
  )

  test("That correct number of imported covers are returned") {
    reset(writeService)

    val coverPage1 = Seq(
      domainCover.copy(id = Some(1)),
      domainCover.copy(id = Some(2)),
      domainCover.copy(id = Some(3)),
      domainCover.copy(id = Some(4))
    )

    val coverPage2 = Seq(
      domainCover.copy(id = Some(5)),
      domainCover.copy(id = Some(6)),
      domainCover.copy(id = Some(7)),
      domainCover.copy(id = Some(8))
    )

    val coverPages = Iterator(Success(coverPage1), Success(coverPage2))

    when(draftConceptRepository.updateIdCounterToHighestId()(any[DBSession])).thenReturn(0)
    when(listingApiClient.getChunks).thenReturn(coverPages)
    when(imageApiClient.getImage(any[String])).thenReturn(
      Success(
        DomainImageMeta(
          id = 123,
          alttexts = Seq(ImageAltText("blabla", "nb"))
        )))

    var coverCheck = 0 // Just using this to fail one of the imported concepts
    when(writeService.insertListingImportedConcepts(any[Seq[(domain.Concept, Long)]], any[Boolean]))
      .thenAnswer((i: InvocationOnMock) => {
        val incConcepts = i.getArgument[Seq[(domain.Concept, Long)]](0)
        incConcepts.map(x => {
          coverCheck += 1
          if (coverCheck == 2 || coverCheck == 5) {
            Failure(ConceptExistsAlreadyException(s"the concept already exists with listing_id of ${x._2}."))
          } else {
            Success(x._1)
          }
        })
      })

    val results = service.importListings(false)
    results.get.numSuccessfullyImportedConcepts should be(6)
    results.get.totalAttemptedImportedConcepts should be(8)
  }

  test("That correct warnings of imported covers are returned") {
    reset(writeService)
    val coverPage1 = Seq(
      domainCover.copy(id = Some(1)),
      domainCover.copy(id = Some(2)),
      domainCover.copy(id = Some(3)),
      domainCover.copy(id = Some(4))
    )

    val coverPage2 = Seq(
      domainCover.copy(id = Some(5)),
      domainCover.copy(id = Some(6), theme = "someunrecognizedtheme"),
      domainCover.copy(id = Some(7)),
      domainCover.copy(id = Some(8))
    )

    val coverPages = Iterator(Success(coverPage1), Success(coverPage2))

    when(draftConceptRepository.updateIdCounterToHighestId()(any[DBSession])).thenReturn(0)
    when(listingApiClient.getChunks).thenReturn(coverPages)
    when(imageApiClient.getImage(any[String])).thenReturn(
      Success(
        DomainImageMeta(
          id = 123,
          alttexts = Seq(ImageAltText("blabla", "nb"))
        )))

    var coverCheckIdx = 0 // Just using this to fail one of the imported concepts
    when(writeService.insertListingImportedConcepts(any[Seq[(domain.Concept, Long)]], any[Boolean]))
      .thenAnswer((i: InvocationOnMock) => {
        val incConcepts = i.getArgument[Seq[(domain.Concept, Long)]](0)
        incConcepts.map(x => {
          coverCheckIdx += 1
          if (coverCheckIdx == 2) {
            Failure(ConceptExistsAlreadyException(s"The concept already exists with listing_id of ${x._2}."))
          } else { Success(x._1) }
        })
      })

    val expectedWarnings = Seq(
      "The concept already exists with listing_id of 2.",
      "Imported listing cover with id 6, could not be connected to taxonomy..."
    )

    val results = service.importListings(false)
    results.get.warnings should be(expectedWarnings)
  }

}
