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
import no.ndla.conceptapi.repository.UnitSuite
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

    val updateWith = UpdatedConcept("nb", Some("heisann"), None, None)
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

    val updateWith = UpdatedConcept("nn", None, Some("Nytt innhald"), None)
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

    val updateWith = UpdatedConcept("en", Some("Title"), Some("My content"), None)
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
        ))
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

}
