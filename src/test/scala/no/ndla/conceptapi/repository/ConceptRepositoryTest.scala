package no.ndla.conceptapi.repository

import java.net.Socket

import no.ndla.conceptapi.model.api.{Concept, ConceptContent}
import no.ndla.conceptapi.{ConceptApiProperties, DBMigrator, IntegrationSuite, TestData, TestEnvironment}
import scalikejdbc.{ConnectionPool, DB, DataSourceConnectionPool}
import no.ndla.conceptapi.TestData._
import scala.util.{Success, Try}

class ConceptRepositoryTest extends IntegrationSuite with TestEnvironment {

  var repository: ConceptRepository = _
  def databaseIsAvailable: Boolean = Try(repository.conceptCount).isSuccess
//
//  override def beforeEach(): Unit = {
//    repository = new ConceptRepository()
//    if (databaseIsAvailable) {
//      emptyTestDatabase
//    }
//  }

  override def beforeAll(): Unit = {
    val datasource = testDataSource
    if (serverIsListening) {
      ConnectionPool.singleton(new DataSourceConnectionPool(datasource))
      DBMigrator.migrate(datasource)
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

  test("Updating an concept should work as expected") {
    assume(databaseIsAvailable, "Database is unavailable")
    val art1 = domainConcept.copy(id = Some(1))
    val art2 = domainConcept.copy(id = Some(2))
    val art3 = domainConcept.copy(id = Some(3))

    repository.insert(art1)
    repository.insert(art2)
    repository.insert(art3)


    val updatedContent = Seq(ConceptContent("What u do mr", "nb"))
    repository.update(art1.copy(ConceptContent = updatedContent))

    repository.withId(art1.id.get).get.content should be(updatedContent)
    repository.withId(art2.id.get).get.content should be(art2.content)
    repository.withId(art3.id.get).get.content should be(art3.content)
  }


  //Some(api.ConceptContent("Innhold for begrep", "nb"))

}
