/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import java.util.Date

import no.ndla.conceptapi.model.api.{Copyright, NotFoundException, UpdatedConcept}
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.{TestData, TestEnvironment}
import no.ndla.conceptapi.UnitSuite
import org.mockito.Mockito._

import scala.util.{Failure, Success}

class ConverterServiceTest extends UnitSuite with TestEnvironment {

  val service = new ConverterService

  test("toApiConcept converts a domain.Concept to an api.Concept with defined language") {
    service.toApiConcept(TestData.domainConcept, "nn", fallback = false) should be(
      Success(TestData.sampleNnApiConcept)
    )
    service.toApiConcept(TestData.domainConcept, "nb", fallback = false) should be(
      Success(TestData.sampleNbApiConcept)
    )
  }

  test("toApiConcept failure if concept not found in specified language without fallback") {
    service.toApiConcept(TestData.domainConcept, "hei", fallback = false) should be(
      Failure(
        NotFoundException(s"The concept with id ${TestData.domainConcept.id.get} and language 'hei' was not found.",
                          TestData.domainConcept.supportedLanguages.toSeq))
    )
  }

  test("toApiConcept success if concept not found in specified language, but with fallback") {
    service.toApiConcept(TestData.domainConcept, "hei", fallback = true) should be(
      Success(TestData.sampleNbApiConcept)
    )
  }

  test("toDomainConcept updates title in concept correctly") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val updateWith = UpdatedConcept("nb", Some("heisann"), None, None, None, None, None, None, Right(Some(42L)))
    service.toDomainConcept(TestData.domainConcept, updateWith) should be(
      TestData.domainConcept.copy(
        title = Seq(
          domain.ConceptTitle("Tittelur", "nn"),
          domain.ConceptTitle("heisann", "nb")
        ),
        updated = updated
      )
    )
  }

  test("toDomainConcept updates content in concept correctly") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val updateWith = UpdatedConcept("nn", None, Some("Nytt innhald"), None, None, None, None, None, Right(Some(42L)))
    service.toDomainConcept(TestData.domainConcept, updateWith) should be(
      TestData.domainConcept.copy(
        content = Seq(
          domain.ConceptContent("Innhold", "nb"),
          domain.ConceptContent("Nytt innhald", "nn")
        ),
        updated = updated
      )
    )
  }

  test("toDomainConcept adds new language in concept correctly") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val updateWith =
      UpdatedConcept("en", Some("Title"), Some("My content"), None, None, None, None, None, Right(Some(42L)))
    service.toDomainConcept(TestData.domainConcept, updateWith) should be(
      TestData.domainConcept.copy(
        title = Seq(domain.ConceptTitle("Tittel", "nb"),
                    domain.ConceptTitle("Tittelur", "nn"),
                    domain.ConceptTitle("Title", "en")),
        content = Seq(
          domain.ConceptContent("Innhold", "nb"),
          domain.ConceptContent("Innhald", "nn"),
          domain.ConceptContent("My content", "en")
        ),
        updated = updated
      )
    )
  }

  test("toDomainConcept updates copyright correctly") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val updateWith = UpdatedConcept(
      "nn",
      None,
      Some("Nytt innhald"),
      None,
      Option(
        Copyright(
          None,
          None,
          Seq(api.Author("Photographer", "Photographer")),
          Seq(api.Author("Photographer", "Photographer")),
          Seq(api.Author("Photographer", "Photographer")),
          None,
          None,
          None
        )
      ),
      None,
      None,
      None,
      Right(Some(42L))
    )
    service.toDomainConcept(TestData.domainConcept, updateWith) should be(
      TestData.domainConcept.copy(
        content = Seq(
          domain.ConceptContent("Innhold", "nb"),
          domain.ConceptContent("Nytt innhald", "nn")
        ),
        copyright = Option(
          domain.Copyright(
            None,
            None,
            Seq(domain.Author("Photographer", "Photographer")),
            Seq(domain.Author("Photographer", "Photographer")),
            Seq(domain.Author("Photographer", "Photographer")),
            None,
            None,
            None
          )),
        updated = updated
      )
    )
  }

  test("toDomainConcept deletes articleId when getting null as a parameter") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val updateWith =
      UpdatedConcept("nb", None, None, None, None, None, None, None, Left(null))
    service.toDomainConcept(TestData.domainConcept, updateWith) should be(
      TestData.domainConcept.copy(
        articleId = None,
        updated = updated
      )
    )
  }

  test("toDomainConcept updates articleId when getting new articleId as a parameter") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val updateWith =
      UpdatedConcept("nb", None, None, None, None, None, None, None, Right(Some(55)))
    service.toDomainConcept(TestData.domainConcept, updateWith) should be(
      TestData.domainConcept.copy(
        articleId = Some(55L),
        updated = updated
      )
    )
  }

  test("toDomainConcept does nothing to articleId when getting None as a parameter") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val updateWith =
      UpdatedConcept("nb", None, None, None, None, None, None, None, Right(None))
    service.toDomainConcept(TestData.domainConcept, updateWith) should be(
      TestData.domainConcept.copy(
        updated = updated
      )
    )
  }

  test("toDomainConcept update concept with ID updates articleId when getting new articleId as a parameter") {
    val today = new Date()
    when(clock.now()).thenReturn(today)

    val updateWith =
      UpdatedConcept(
        "nb",
        Some("Tittel"),
        Some("Innhold"),
        Some(api.NewConceptMetaImage("1", "Hei")),
        None,
        None,
        Some(Seq("stor", "kaktus")),
        Some(Seq("urn:subject:3")),
        Right(Some(13L))
      )
    service.toDomainConcept(112L, updateWith) should be(
      TestData.domainConcept_toDomainUpdateWithId.copy(
        created = today,
        updated = today,
        articleId = Some(13L),
      )
    )
  }

  test("toDomainConcept update concept with ID sets articleId to None when articleId is not specified") {
    val today = new Date()
    when(clock.now()).thenReturn(today)

    val updateWith =
      UpdatedConcept(
        "nb",
        Some("Tittel"),
        Some("Innhold"),
        Some(api.NewConceptMetaImage("1", "Hei")),
        None,
        None,
        Some(Seq("stor", "kaktus")),
        Some(Seq("urn:subject:3")),
        Left(null)
      )
    service.toDomainConcept(112L, updateWith) should be(
      TestData.domainConcept_toDomainUpdateWithId.copy(
        created = today,
        updated = today,
        articleId = None,
      )
    )
  }

}
