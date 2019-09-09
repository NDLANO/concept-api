/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import java.util.Date

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
    theme = "sometheme"
  )

  val coverPage1 = Seq(
    domainCover.copy(id = Some(1), oldNodeId = Some(1111), articleApiId = 111),
    domainCover.copy(id = Some(2), oldNodeId = Some(2222), articleApiId = 222),
    domainCover.copy(id = Some(3), oldNodeId = Some(3333), articleApiId = 333),
    domainCover.copy(id = Some(4), oldNodeId = Some(4444), articleApiId = 444)
  )

  val coverPage2 = Seq(
    domainCover.copy(id = Some(5), oldNodeId = Some(5555), articleApiId = 555),
    domainCover.copy(id = Some(6), oldNodeId = Some(6666), articleApiId = 666),
    domainCover.copy(id = Some(7), oldNodeId = Some(7777), articleApiId = 777),
    domainCover.copy(id = Some(8), oldNodeId = Some(8888), articleApiId = 888)
  )

  test("That correct number of imported covers are returned") {
    reset(writeService)
    val coverPages = Iterator(Success(coverPage1), Success(coverPage2))

    when(conceptRepository.updateIdCounterToHighestId()(any[DBSession])).thenReturn(0)
    when(listingApiClient.getChunks).thenReturn(coverPages)
    when(imageApiClient.getImage(any[String])).thenReturn(Failure(new RuntimeException("blabla")))
    when(taxonomyApiClient.getSubjectIdsForIds(any[Option[Long]], any[Long])).thenReturn(Set.empty[String])

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
    val coverPages = Iterator(Success(coverPage1), Success(coverPage2))

    when(conceptRepository.updateIdCounterToHighestId()(any[DBSession])).thenReturn(0)
    when(listingApiClient.getChunks).thenReturn(coverPages)
    when(imageApiClient.getImage(any[String])).thenReturn(Failure(new RuntimeException("blabla")))
    when(taxonomyApiClient.getSubjectIdsForIds(any[Option[Long]], any[Long])).thenAnswer((i: InvocationOnMock) => {
      val apiId = i.getArgument[Long](1)
      if (apiId == 666) Set.empty[String] else Set("urn:subject:5")
    })

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
