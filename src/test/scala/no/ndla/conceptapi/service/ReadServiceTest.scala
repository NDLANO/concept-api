/*
 * Part of NDLA concept-api.
 * Copyright (C) 2020 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.{TestEnvironment, UnitSuite}
import org.mockito.Mockito._

class ReadServiceTest extends UnitSuite with TestEnvironment {
  override val converterService = new ConverterService

  val service = new ReadService()

  test("Checks that filter by language works as it should") {

    when(conceptRepository.everyTagFromEveryConcept).thenReturn(
      List(
        List(
          domain.ConceptTags(Seq("konge", "bror"), "nb"),
          domain.ConceptTags(Seq("konge", "brur"), "nn"),
          domain.ConceptTags(Seq("king", "bro"), "en"),
          domain.ConceptTags(Seq("zing", "xiongdi"), "zh"),
        ),
        List(
          domain.ConceptTags(Seq("konge", "lol", "meme"), "nb"),
          domain.ConceptTags(Seq("konge", "lel", "meem"), "nn"),
          domain.ConceptTags(Seq("king", "lul", "maymay"), "en"),
          domain.ConceptTags(Seq("zing", "kek", "mimi"), "zh")
        )
      ))

    val result_nb = service.allTagsFromConcepts("nb", fallback = false)
    val result_nn = service.allTagsFromConcepts("nn", fallback = false)
    val result_en = service.allTagsFromConcepts("en", fallback = false)
    val result_zh = service.allTagsFromConcepts("zh", fallback = false)
    val result_all = service.allTagsFromConcepts("all", fallback = false)

    result_nb should equal(List("konge", "bror", "lol", "meme"))
    result_nn should equal(List("konge", "brur", "lel", "meem"))
    result_en should equal(List("king", "bro", "lul", "maymay"))
    result_zh should equal(List("zing", "xiongdi", "kek", "mimi"))
    result_all should equal(List("konge", "bror", "lol", "meme"))
  }
}
