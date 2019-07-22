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
import scala.util.{Failure, Try}
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
    TestData.sampleNbApiConcept.copy(id = conceptId.toLong)

  val domainConcept: domain.Concept = TestData.sampleNbDomainConcept.copy(id = Option(conceptId.toLong))

  override def beforeEach(): Unit = {
    Mockito.reset(conceptRepository)

    when(conceptRepository.withId(conceptId)).thenReturn(Option(domainConcept))
    when(conceptRepository.update(any[Concept])(any[DBSession]))
      .thenAnswer((invocation: InvocationOnMock) => {
        val arg = invocation.getArgument[Concept](0)
        Try(arg)
      })

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
