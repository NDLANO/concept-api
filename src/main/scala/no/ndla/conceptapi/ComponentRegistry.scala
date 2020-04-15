/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi

import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.hikari.HikariDataSource
import no.ndla.conceptapi.controller.{
  DraftConceptController,
  DraftNdlaController,
  HealthController,
  InternController,
  NdlaController,
  PublishedConceptController
}
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.integration.{
  ArticleApiClient,
  DataSource,
  Elastic4sClient,
  Elastic4sClientFactory,
  ImageApiClient,
  ListingApiClient,
  NdlaE4sClient
}
import no.ndla.conceptapi.repository.{ConceptRepository, PublishedConceptRepository}
import no.ndla.conceptapi.service.search.{
  ConceptIndexService,
  ConceptSearchService,
  IndexService,
  PublishedConceptIndexService,
  PublishedConceptSearchService,
  SearchConverterService,
  SearchService
}
import no.ndla.conceptapi.service.{
  Clock,
  ConverterService,
  ImportService,
  ReadService,
  StateTransitionRules,
  WriteService
}
import no.ndla.conceptapi.validation.ContentValidator
import no.ndla.network.NdlaClient
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}

object ComponentRegistry
    extends DraftConceptController
    with PublishedConceptController
    with DraftNdlaController
    with Clock
    with User
    with WriteService
    with ContentValidator
    with ReadService
    with ConverterService
    with StateTransitionRules
    with ConceptRepository
    with PublishedConceptRepository
    with DataSource
    with LazyLogging
    with HealthController
    with ConceptSearchService
    with PublishedConceptSearchService
    with ImportService
    with SearchService
    with SearchConverterService
    with Elastic4sClient
    with ConceptIndexService
    with PublishedConceptIndexService
    with IndexService
    with InternController
    with ArticleApiClient
    with ListingApiClient
    with ImageApiClient
    with NdlaClient {

  lazy val conceptController = new DraftConceptController
  lazy val publishedConceptController = new PublishedConceptController
  lazy val healthController = new HealthController
  lazy val internController = new InternController

  lazy val conceptRepository = new ConceptRepository
  lazy val publishedConceptRepository = new PublishedConceptRepository

  lazy val conceptSearchService = new ConceptSearchService
  lazy val searchConverterService = new SearchConverterService
  lazy val conceptIndexService = new ConceptIndexService
  lazy val publishedConceptIndexService = new PublishedConceptIndexService
  lazy val publishedConceptSearchService = new PublishedConceptSearchService

  var e4sClient: NdlaE4sClient = Elastic4sClientFactory.getClient()

  lazy val ndlaClient = new NdlaClient
  lazy val articleApiClient = new ArticleApiClient
  lazy val listingApiClient = new ListingApiClient
  lazy val imageApiClient = new ImageApiClient

  lazy val importService = new ImportService

  lazy val writeService = new WriteService
  lazy val readService = new ReadService
  lazy val converterService = new ConverterService
  lazy val user = new User
  lazy val clock = new SystemClock
  lazy val contentValidator = new ContentValidator

  def connectToDatabase(): Unit =
    ConnectionPool.singleton(new DataSourceConnectionPool(dataSource))

  implicit val swagger: ConceptSwagger = new ConceptSwagger

  lazy val resourcesApp = new ResourcesApp

  override val dataSource: HikariDataSource = DataSource.getHikariDataSource
  connectToDatabase()

}
