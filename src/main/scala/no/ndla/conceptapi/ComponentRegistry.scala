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
import no.ndla.conceptapi.integration.DataSource
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}


object ComponentRegistry extends ConceptController with DataSource with LazyLogging {

  lazy val conceptController = new ConceptController()

  def connectToDatabase(): Unit = ConnectionPool.singleton(new DataSourceConnectionPool(dataSource))

  implicit val swagger: ConceptSwagger = new ConceptSwagger

  override val dataSource: HikariDataSource = DataSource.getHikariDataSource
  connectToDatabase()


}


