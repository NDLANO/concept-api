/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi

import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.hikari.HikariDataSource
import no.ndla.conceptapi.controller.ConceptController
import no.ndla.conceptapi.ConceptSwagger
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.integration.DataSource
import no.ndla.conceptapi.repository.ConceptRepository
import no.ndla.conceptapi.service.{
  Clock,
  ConverterService,
  ReadService,
  WriteService
}
import no.ndla.conceptapi.validation.ContentValidator
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}

object ComponentRegistry
    extends ConceptController
    with Clock
    with User
    with WriteService
    with ContentValidator
    with ReadService
    with ConverterService
    with ConceptRepository
    with DataSource
    with LazyLogging {

  lazy val conceptController = new ConceptController
  lazy val conceptRepository = new ConceptRepository
  lazy val writeService = new WriteService
  lazy val readService = new ReadService
  lazy val converterService = new ConverterService
  lazy val user = new User
  lazy val clock = new SystemClock
  lazy val contentValidator = new ContentValidator

  def connectToDatabase(): Unit =
    ConnectionPool.singleton(new DataSourceConnectionPool(dataSource))

  implicit val swagger: ConceptSwagger = new ConceptSwagger

  override val dataSource: HikariDataSource = DataSource.getHikariDataSource
  connectToDatabase()

}
