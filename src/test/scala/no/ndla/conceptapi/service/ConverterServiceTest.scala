/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import no.ndla.conceptapi.model.api.NotFoundException
import no.ndla.conceptapi.{TestData, TestEnvironment}
import no.ndla.conceptapi.repository.UnitSuite

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
  // TODO: def toDomainConcept(toMergeInto: domain.Concept, updateConcept: api.UpdatedConcept):

}
