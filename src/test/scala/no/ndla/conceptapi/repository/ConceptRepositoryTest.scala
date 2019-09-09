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

  test("That getting subjects works as expected") {
    assume(databaseIsAvailable, "Database is unavailable")
    val concept1 = domainConcept.copy(subjectIds = Set("urn:subject:1", "urn:subject:2"))
    val concept2 = domainConcept.copy(subjectIds = Set("urn:subject:1", "urn:subject:19"))
    val concept3 = domainConcept.copy(subjectIds = Set("urn:subject:12"))

    repository.insert(concept1)
    repository.insert(concept2)
    repository.insert(concept3)

    repository.allSubjectIds should be(
      Set(
        "urn:subject:1",
        "urn:subject:2",
        "urn:subject:12",
        "urn:subject:19"
      )
    )
  }

  test("Inserting and fetching with listing id works as expected") {
    val concept1 = domainConcept.copy(title = Seq(domain.ConceptTitle("Really good title", "nb")))
    val concept2 = domainConcept.copy(title = Seq(domain.ConceptTitle("Not so bad title", "nb")))
    val concept3 = domainConcept.copy(title = Seq(domain.ConceptTitle("Whatchu doin", "nb")))

    val insertedConcept1 = repository.insertwithListingId(concept1, 55555)
    val insertedConcept2 = repository.insertwithListingId(concept2, 66666)
    val insertedConcept3 = repository.insertwithListingId(concept3, 77777)

    val result1 = repository.withListingId(55555)
    val expected1 =
      Some(concept1.copy(id = insertedConcept1.id, created = result1.get.created, updated = result1.get.updated))
    result1 should be(expected1)

    val result2 = repository.withListingId(66666)
    val expected2 =
      Some(concept2.copy(id = insertedConcept2.id, created = result2.get.created, updated = result2.get.updated))
    result2 should be(expected2)

    val result3 = repository.withListingId(77777)
    val expected3 =
      Some(concept3.copy(id = insertedConcept3.id, created = result3.get.created, updated = result3.get.updated))
    result3 should be(expected3)
  }

}
