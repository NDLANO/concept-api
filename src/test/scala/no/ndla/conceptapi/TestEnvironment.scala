/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi

import com.zaxxer.hikari.HikariDataSource
import no.ndla.conceptapi.integration.DataSource
import no.ndla.conceptapi.repository.ConceptRepository
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import reflect.ClassTag
import org.mockito.stubbing.Answer
import org.mockito.MockSettings

trait TestEnvironment extends ConceptRepository with MockitoSugar with DataSource {

  val conceptRepository = mock[ConceptRepository]
  val dataSource = mock[HikariDataSource]

}
