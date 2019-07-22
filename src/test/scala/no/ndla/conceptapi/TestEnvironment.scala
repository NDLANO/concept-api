/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi

import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.hikari.HikariDataSource
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.controller.ConceptController
import no.ndla.conceptapi.integration.{DataSource, Elastic4sClient, NdlaE4sClient}
import no.ndla.conceptapi.repository.ConceptRepository
import no.ndla.conceptapi.service.search.{
  ConceptIndexService,
  ConceptSearchService,
  IndexService,
  SearchConverterService,
  SearchService
}
import no.ndla.conceptapi.service.{Clock, ConverterService, ReadService, WriteService}
import no.ndla.conceptapi.validation.ContentValidator
import org.scalatest.mockito.MockitoSugar

trait TestEnvironment
    extends ConceptRepository
    with ConceptController
    with SearchConverterService
    with ConceptIndexService
    with ConceptSearchService
    with IndexService
    with Elastic4sClient
    with SearchService
    with LazyLogging
    with MockitoSugar
    with DataSource
    with WriteService
    with ReadService
    with ConverterService
    with ContentValidator
    with Clock
    with User {

  val conceptRepository = mock[ConceptRepository]
  val conceptController = mock[ConceptController]
  val searchConverterService = mock[SearchConverterService]
  val conceptIndexService = mock[ConceptIndexService]
  val conceptSearchService = mock[ConceptSearchService]
  val e4sClient = mock[NdlaE4sClient]
  val lazyLogging = mock[LazyLogging]
  val mockitoSugar = mock[MockitoSugar]
  val dataSource = mock[HikariDataSource]
  val writeService = mock[WriteService]
  val readService = mock[ReadService]
  val converterService = mock[ConverterService]
  val contentValidator = mock[ContentValidator]
  val clock = mock[SystemClock]
  val user = mock[User]

}
