/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.repository

import java.net.Socket

import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.{ConceptApiProperties, DBMigrator, IntegrationSuite, TestData, TestEnvironment}
import scalikejdbc.{ConnectionPool, DB, DataSourceConnectionPool}
import no.ndla.conceptapi.TestData._

import scala.util.{Success, Try}
import scalikejdbc._

class ConceptRepositoryTest extends IntegrationSuite with TestEnvironment {

  val repository: ConceptRepository = new ConceptRepository
  def databaseIsAvailable: Boolean = Try(repository.conceptCount).isSuccess

  def emptyTestDatabase = {
    DB autoCommit (implicit session => {
      sql"delete from conceptapitest.conceptdata;".execute.apply()(session)
    })
  }

  override def beforeEach(): Unit = {
    if (databaseIsAvailable) {
      emptyTestDatabase
    }
  }

  override def beforeAll(): Unit = {
    Try {
      val datasource = testDataSource.get
      if (serverIsListening) {
        ConnectionPool.singleton(new DataSourceConnectionPool(datasource))
        DBMigrator.migrate(datasource)
      }
    }
  }

  def serverIsListening: Boolean = {
    Try(new Socket(ConceptApiProperties.MetaServer, ConceptApiProperties.MetaPort)) match {
      case Success(c) =>
        c.close()
        true
      case _ => false
    }
  }

  test("Inserting and Updating an concept should work as expected") {
    assume(databaseIsAvailable, "Database is unavailable")
    val art1 = domainConcept.copy()
    val art2 = domainConcept.copy()
    val art3 = domainConcept.copy()

    val id1 = repository.insert(art1).id.get
    val id2 = repository.insert(art2).id.get
    val id3 = repository.insert(art3).id.get

    val updatedContent = Seq(domain.ConceptContent("What u do mr", "nb"))
    repository.update(art1.copy(id = Some(id1), content = updatedContent))

    repository.withId(id1).get.content should be(updatedContent)
    repository.withId(id2).get.content should be(art2.content)
    repository.withId(id3).get.content should be(art3.content)
  }

}
