/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import no.ndla.conceptapi.repository.ConceptRepository
import no.ndla.conceptapi.model.api

trait ReadService {
  this: ConceptRepository with ConceptRepository with ConverterService =>
  val readService: ReadService

  class ReadService {

    def conceptWithId(id: Long, language: String): Option[api.Concept] =
      conceptRepository
        .withId(id)
        .map(concept => converterService.toApiConcept(concept, language))

  }
}
