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

  val today: Date = DateTime.now().toDate
  val yesterday: Date = DateTime.now().minusDays(1).toDate
  val service = new WriteService()
  val conceptId = 13

  val concept: api.Concept =
    TestData.sampleNbApiConcept.copy(id = conceptId.toLong, updated = today)

  val domainConcept: domain.Concept = TestData.sampleNbDomainConcept.copy(id = Some(conceptId.toLong))

  override def beforeEach(): Unit = {
    Mockito.reset(conceptRepository)

    when(conceptRepository.withId(conceptId)).thenReturn(Option(domainConcept))
    when(conceptRepository.update(any[Concept])(any[DBSession]))
      .thenAnswer((invocation: InvocationOnMock) => {
        val arg = invocation.getArgument[Concept](0)
        Try(arg)
      })
    when(contentValidator.validateConcept(any[Concept], any[Boolean])).thenReturn(Success(domainConcept))
    when(converterService.toDomainConcept(any[domain.Concept], any[api.UpdatedConcept])).thenReturn(domainConcept)
    //converterService.toDomainConcept(concept, updatedConcept)
    //def toDomainConcept(toMergeInto: domain.Concept, updateConcept: api.UpdatedConcept)
    when(converterService.toApiConcept(any[domain.Concept], any(), any())).thenReturn(Success(concept))
    when(conceptIndexService.indexDocument(any[Concept])).thenAnswer((invocation: InvocationOnMock) =>
      Try(invocation.getArgument[Concept](0)))

//            api.Concept(
    //            concept.id.get,
    //            Some(title),
    //            Some(content),
    //            concept.copyright.map(toApiCopyright),
    //            concept.created,
    //            concept.updated,
    //            concept.supportedLanguages
    //          )
  }


 /* test("newConcept should insert a given concept") {
    when(conceptRepository.insert(any[Concept])(any[DBSession])).thenReturn(domainConcept)//should not be domain??
    when(contentValidator.validateConcept(any[Concept], any[Boolean])).thenReturn(Success(domainConcept))//should not be domain??
    when(conceptRepository.newConceptId()(any[DBSession])).thenReturn(Success(1: Long))

    service
      .newConcept(TestData.sampleNewConcept)
      .get
      .id
      .toString should equal(concept.id.toString)
    verify(conceptRepository, times(1)).insert(any[Concept])
  }*/


  /*test("That updateAgreement updates only content properly") {
    val newContent = "NyContentTest"
    val updatedApiAgreement = api.UpdatedAgreement(None, Some(newContent), None)
    val expectedAgreement = agreement.copy(content = newContent, updated = today)

    service.updateAgreement(agreementId, updatedApiAgreement, TestData.userWithWriteAccess).get should equal(
      converterService.toApiAgreement(expectedAgreement))
  }*/



  test("That update function updates only content properly") {
    val newContent = "NewContentTest"
    val updatedApiConcept = api.UpdatedConcept("en", None, content=Some(newContent), None)
    val expectedConcept = domainConcept.copy(content = Seq(ConceptContent(newContent, "en")), updated = today)
    service.updateConcept(conceptId, updatedApiConcept).get should equal(
      converterService.toApiConcept(expectedConcept,"en", fallback=true))
  }


/*
  test("That update function updates only content properly") {
    val newContent = "NyContentTest"
    val updatedApiConcept = // API
      api.UpdatedConcept(
        "en",
        None,
        Some(newContent),
        None)
    val expectedConcept = // DOMAIN
      domainConcept.copy(
        content = Seq(ConceptContent(newContent, "en")),
        updated = today)
    val expectedConcept2 = // API
      concept.copy(
        content = Option(api.ConceptContent(newContent, "en")),
        updated = today)
    println(service.updateConcept(conceptId,
      updatedApiConcept))
    println(expectedConcept2)
    service.updateConcept(updatedApiConcept,
      updatedApiConcept) should equal (expectedConcept2)
  }*/





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
