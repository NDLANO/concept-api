/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import java.util.Date

import scala.util.Failure
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.{TestData, TestEnvironment, UnitSuite}
import org.joda.time.DateTime
import org.mockito.Mockito

import scala.util.{Failure, Success, Try}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Mockito}

class WriteServiceTest extends UnitSuite with TestEnvironment {


  val today: Date = DateTime.now().toDate
  val yesterday: Date = DateTime.now().minusDays(1).toDate
  val service = new WriteService()
  val conceptId = 13


  val concept: api.Concept =
    TestData.sampleApiConcept.copy(id = conceptId.toLong, created = yesterday, updated = today)
  println("concept", concept)


  override def beforeEach(): Unit = {
    Mockito.reset( conceptRepository )
/*
    when(conceptRepository.withId(conceptId)).thenReturn(Option(domain.concept))
    when(conceptRepository.getExternalIdsFromId(any[Long])(any[DBSession])).thenReturn(List("1234"))
    when(clock.now()).thenReturn(today)
    when(conceptRepository.updateConcept(any[Concept], any[Boolean])(any[DBSession]))
      .thenAnswer((invocation: InvocationOnMock) => {
        val arg = invocation.getArgument[Article](0)
        Try(arg.copy(revision = Some(arg.revision.get + 1)))
      })
*/
  }



  test("That delete article should fail when only one language") {
    val Failure(result) = service.deleteLanguage(concept.id, "nb")
    println(result.getMessage)
    result.getMessage should equal("Only one language left")
  }
/*
  test("That delete article removes language from all languagefields") {
    val article =
      TestData.sampleDomainArticle.copy(id = Some(3),
        title = Seq(ArticleTitle("title", "nb"), ArticleTitle("title", "nn")))
    val articleCaptor: ArgumentCaptor[Article] = ArgumentCaptor.forClass(classOf[Article])

    when(draftRepository.withId(anyLong())).thenReturn(Some(article))
    service.deleteLanguage(article.id.get, "nn")
    verify(draftRepository).updateArticle(articleCaptor.capture(), anyBoolean())

    articleCaptor.getValue.title.length should be(1)
  }
*/

}
