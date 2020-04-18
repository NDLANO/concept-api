/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.repository

import java.net.Socket
import java.util.Date

import no.ndla.conceptapi._
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.domain.PublishedConcept
import scalikejdbc.{ConnectionPool, DB, DataSourceConnectionPool, _}

import scala.util.{Success, Try}

class PublishedConceptRepositoryTest extends IntegrationSuite with TestEnvironment {

  val repository: PublishedConceptRepository = new PublishedConceptRepository
  def databaseIsAvailable: Boolean = Try(repository.conceptCount).isSuccess

  def emptyTestDatabase = {
    DB autoCommit (implicit session => {
      sql"delete from ${PublishedConcept.table};".execute.apply()(session)
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

  test("That inserting and updating works") {
    val consistentDate = new Date(0)
    val concept1 = TestData.domainConcept.copy(
      id = Some(10),
      title = Seq(domain.ConceptTitle("Yes", "nb")),
      created = consistentDate,
      updated = consistentDate
    )
    val concept2 = TestData.domainConcept.copy(
      id = Some(10),
      title = Seq(domain.ConceptTitle("No", "nb")),
      created = consistentDate,
      updated = consistentDate
    )
    val concept3 = TestData.domainConcept.copy(
      id = Some(11),
      title = Seq(domain.ConceptTitle("Yolo", "nb")),
      created = consistentDate,
      updated = consistentDate
    )

    repository.insertOrUpdate(concept1)
    repository.insertOrUpdate(concept3)
    repository.withId(10) should be(Some(concept1))
    repository.withId(11) should be(Some(concept3))

    repository.insertOrUpdate(concept2)
    repository.withId(10) should be(Some(concept2))
    repository.withId(11) should be(Some(concept3))
  }

  test("That deletion works as expected") {
    val consistentDate = new Date(0)
    val concept1 = TestData.domainConcept.copy(
      id = Some(10),
      title = Seq(domain.ConceptTitle("Yes", "nb")),
      created = consistentDate,
      updated = consistentDate
    )
    val concept2 = TestData.domainConcept.copy(
      id = Some(11),
      title = Seq(domain.ConceptTitle("Yolo", "nb")),
      created = consistentDate,
      updated = consistentDate
    )

    repository.insertOrUpdate(concept1)
    repository.insertOrUpdate(concept2)
    repository.withId(10) should be(Some(concept1))
    repository.withId(11) should be(Some(concept2))

    repository.delete(10).isSuccess should be(true)

    repository.withId(10) should be(None)
    repository.withId(11) should be(Some(concept2))

    repository.delete(10).isSuccess should be(false)
  }

  test("That getting subjects works as expected") {
    assume(databaseIsAvailable, "Database is unavailable")
    val concept1 = TestData.domainConcept.copy(id = Some(1), subjectIds = Set("urn:subject:1", "urn:subject:2"))
    val concept2 = TestData.domainConcept.copy(id = Some(2), subjectIds = Set("urn:subject:1", "urn:subject:19"))
    val concept3 = TestData.domainConcept.copy(id = Some(3), subjectIds = Set("urn:subject:12"))

    repository.insertOrUpdate(concept1)
    repository.insertOrUpdate(concept2)
    repository.insertOrUpdate(concept3)

    repository.allSubjectIds should be(
      Set(
        "urn:subject:1",
        "urn:subject:2",
        "urn:subject:12",
        "urn:subject:19"
      )
    )
  }

  test("Fetching concepts tags works as expected") {
    assume(databaseIsAvailable, "Database is unavailable")
    val concept1 =
      TestData.domainConcept.copy(
        id = Some(1),
        tags = Seq(
          domain.ConceptTags(Seq("konge", "bror"), "nb"),
          domain.ConceptTags(Seq("konge", "brur"), "nn"),
          domain.ConceptTags(Seq("king", "bro"), "en"),
          domain.ConceptTags(Seq("zing", "xiongdi"), "zh")
        )
      )
    val concept2 =
      TestData.domainConcept.copy(
        id = Some(2),
        tags = Seq(
          domain.ConceptTags(Seq("konge", "lol", "meme"), "nb"),
          domain.ConceptTags(Seq("konge", "lel", "meem"), "nn"),
          domain.ConceptTags(Seq("king", "lul", "maymay"), "en"),
          domain.ConceptTags(Seq("zing", "kek", "mimi"), "zh")
        )
      )
    val concept3 =
      TestData.domainConcept.copy(
        id = Some(3),
        tags = Seq()
      )

    repository.insertOrUpdate(concept1)
    repository.insertOrUpdate(concept2)
    repository.insertOrUpdate(concept3)

    repository.everyTagFromEveryConcept should be(
      List(
        List(
          domain.ConceptTags(Seq("konge", "bror"), "nb"),
          domain.ConceptTags(Seq("konge", "brur"), "nn"),
          domain.ConceptTags(Seq("king", "bro"), "en"),
          domain.ConceptTags(Seq("zing", "xiongdi"), "zh"),
        ),
        List(
          domain.ConceptTags(Seq("konge", "lol", "meme"), "nb"),
          domain.ConceptTags(Seq("konge", "lel", "meem"), "nn"),
          domain.ConceptTags(Seq("king", "lul", "maymay"), "en"),
          domain.ConceptTags(Seq("zing", "kek", "mimi"), "zh")
        )
      )
    )
  }

  test("That count works as expected") {
    val consistentDate = new Date(0)
    val concept1 = TestData.domainConcept.copy(
      id = Some(10),
      created = consistentDate,
      updated = consistentDate
    )
    val concept2 = TestData.domainConcept.copy(
      id = Some(11),
      created = consistentDate,
      updated = consistentDate
    )
    val concept3 = TestData.domainConcept.copy(
      id = Some(11),
      created = consistentDate,
      updated = consistentDate
    )
    val concept4 = TestData.domainConcept.copy(
      id = Some(12),
      created = consistentDate,
      updated = consistentDate
    )
    repository.conceptCount should be(0)

    repository.insertOrUpdate(concept1)
    repository.conceptCount should be(1)

    repository.insertOrUpdate(concept2)
    repository.conceptCount should be(2)

    repository.insertOrUpdate(concept3)
    repository.conceptCount should be(2)

    repository.insertOrUpdate(concept4)
    repository.conceptCount should be(3)
  }

}
