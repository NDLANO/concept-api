/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi

import com.zaxxer.hikari.HikariDataSource
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.controller.ConceptController
import no.ndla.conceptapi.integration.DataSource
import no.ndla.conceptapi.repository.ConceptRepository
import no.ndla.conceptapi.service.{Clock, ConverterService, ReadService, WriteService}
import no.ndla.conceptapi.validation.ContentValidator
import org.scalatest.mockito.MockitoSugar

trait TestEnvironment
    extends ConceptRepository
    with ConceptController
    with MockitoSugar
    with DataSource
    with WriteService
    with ReadService
    with ConverterService
    with ContentValidator
    with Clock
    with User {

  val conceptRepository = mock[ConceptRepository]
  val dataSource = mock[HikariDataSource]
  val conceptController = mock[ConceptController]
  val writeService = mock[WriteService]
  val readService = mock[ReadService]
  val converterService = mock[ConverterService]
  val contentValidator = mock[ContentValidator]
  val clock = mock[SystemClock]
  val user = mock[User]

}
