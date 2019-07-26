/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import java.util.Date

import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.domain._
import no.ndla.conceptapi.{TestData, TestEnvironment, UnitSuite}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._

import scala.util.{Failure, Success, Try}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.invocation.InvocationOnMock
import scalikejdbc.DBSession

class WriteServiceTest extends UnitSuite with TestEnvironment {
  override val converterService = new ConverterService

  val today: Date = DateTime.now().toDate
  val yesterday: Date = DateTime.now().minusDays(1).toDate
  val service = new WriteService()
  val conceptId = 13

  val concept: api.Concept =
    TestData.sampleNbApiConcept.copy(id = conceptId.toLong, updated = today, supportedLanguages = Set("nb"))

  val domainConcept: domain.Concept = TestData.sampleNbDomainConcept.copy(id = Some(conceptId.toLong))

  override def beforeEach(): Unit = {
    Mockito.reset(conceptRepository)

    when(conceptRepository.withId(conceptId)).thenReturn(Option(domainConcept))
    when(conceptRepository.update(any[Concept])(any[DBSession]))
      .thenAnswer((invocation: InvocationOnMock) => {

        Try(invocation.getArgument[Concept](0))
      })
    when(contentValidator.validateConcept(any[Concept], any[Boolean])).thenReturn(Success(domainConcept))
    when(conceptIndexService.indexDocument(any[Concept])).thenAnswer((invocation: InvocationOnMock) =>
      Try(invocation.getArgument[Concept](0)))
    when(clock.now()).thenReturn(today)
  }

  test("newConcept should insert a given Concept") {
    when(conceptRepository.insert(any[Concept])(any[DBSession])).thenReturn(domainConcept)
    when(contentValidator.validateConcept(any[Concept], any[Boolean])).thenReturn(Success(domainConcept))

    service.newConcept(TestData.sampleNewConcept).get.id.toString should equal(domainConcept.id.get.toString)
    verify(conceptRepository, times(1)).insert(any[Concept])
    verify(conceptIndexService, times(1)).indexDocument(any[Concept])
  }

  test("That update function updates only content properly") {
    val newContent = "NewContentTest"
    val updatedApiConcept = api.UpdatedConcept("en", None, content = Some(newContent), None, None)
    val expectedConcept = concept.copy(content = Option(api.ConceptContent(newContent, "en")),
                                       updated = today,
                                       supportedLanguages = Set("nb", "en"))
    service.updateConcept(conceptId, updatedApiConcept).get should equal(expectedConcept)
  }

  test("That update function updates only title properly") {
    val newTitle = "NewTitleTest"
    val updatedApiConcept = api.UpdatedConcept("nn", title = Some(newTitle), None, None, None)
    val expectedConcept = concept.copy(title = Option(api.ConceptTitle(newTitle, "nn")),
                                       updated = today,
                                       supportedLanguages = Set("nb", "nn"))
    service.updateConcept(conceptId, updatedApiConcept).get should equal(expectedConcept)
  }

  test("That updateConcept updates multiple fields properly") {
    val updatedTitle = "NyTittelTestJee"
    val updatedContent = "NyContentTestYepp"
    val updatedCopyright =
      api.Copyright(None, Some("c"), Seq(api.Author("Opphavsmann", "Katrine")), List(), List(), None, None, None)

    val updatedApiConcept = api.UpdatedConcept(
      ("en"),
      Some(updatedTitle),
      Some(updatedContent),
      None,
      Some(updatedCopyright)
    )

    val expectedConcept = concept.copy(
      title = Option(api.ConceptTitle(updatedTitle, "en")),
      content = Option(api.ConceptContent(updatedContent, "en")),
      copyright = Some(
        api.Copyright(None, Some("c"), Seq(api.Author("Opphavsmann", "Katrine")), List(), List(), None, None, None)),
      supportedLanguages = Set("nb", "en")
    )

    service.updateConcept(conceptId, updatedApiConcept) should equal(Success(expectedConcept))

  }

  test("That delete concept should fail when only one language") {
    val Failure(result) = service.deleteLanguage(concept.id, "nb")

    result.getMessage should equal("Only one language left")
  }

  test("That delete concept removes language from all languagefields") {
    val concept =
      TestData.sampleNbDomainConcept.copy(id = Some(3.toLong),
                                          title = Seq(ConceptTitle("title", "nb"), ConceptTitle("title", "nn")))
    val conceptCaptor: ArgumentCaptor[Concept] = ArgumentCaptor.forClass(classOf[Concept])

    when(conceptRepository.withId(anyLong())).thenReturn(Some(concept))

    service.deleteLanguage(concept.id.get, "nn")
    verify(conceptRepository).update(conceptCaptor.capture())

    conceptCaptor.getValue.title.length should be(1)
  }

}
