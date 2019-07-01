/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

import no.ndla.conceptapi.model.api.{NewConcept, UpdatedConcept}
import no.ndla.conceptapi.{ConceptSwagger, TestData, TestEnvironment}
import no.ndla.conceptapi.repository.UnitSuite
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.write
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatra.test.scalatest.ScalatraFunSuite

import scala.util.{Success}

class ConceptControllerTest extends UnitSuite with ScalatraFunSuite with TestEnvironment {
  implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats
  implicit val swagger = new ConceptSwagger
  lazy val controller = new ConceptController
  addServlet(controller, "/test")

  val conceptId = 1
  val lang = "nb"

  val invalidConcept = """{"title": [{"language": "nb", "titlee": "lol"]}""" // typo in "titlee"

  override def beforeEach: Unit = {
    when(user.getUser).thenReturn(TestData.userWithWriteAccess)
  }

  test("/<concept_id> should return 200 if the concept was found") {
    when(readService.conceptWithId(conceptId, lang)).thenReturn(Some(TestData.sampleConcept))

    get(s"/test/$conceptId?language=$lang") {
      status should equal(200)
    }
  }

  test("/<concept_id> should return 404 if the concept was not found") {
    when(readService.conceptWithId(conceptId, lang)).thenReturn(None)

    get(s"/test/$conceptId?language=$lang") {
      status should equal(404)
    }
  }

  test("/<concept_id> should return 400 if the concept was not found") {
    get(s"/test/one") {
      status should equal(400)
    }
  }

  test("POST / should return 400 if body does not contain all required fields") {
    post("/test/", invalidConcept) {
      status should equal(400)
    }
  }

  test("POST / should return 201 on created") {
    when(
      writeService
        .newConcept(any[NewConcept]))
      .thenReturn(Success(TestData.sampleConcept))
    post("/test/", write(TestData.sampleNewConcept), headers = Map("Authorization" -> TestData.authHeaderWithWriteRole)) {
      status should equal(201)
    }
  }

  test("POST / should return 403 if no write role") {
    when(user.getUser).thenReturn(TestData.userWithNoRoles)
    when(
      writeService
        .newConcept(any[NewConcept]))
      .thenReturn(Success(TestData.sampleConcept))
    post("/test/", write(TestData.sampleNewConcept), headers = Map("Authorization" -> TestData.authHeaderWithWriteRole)) {
      status should equal(403)
    }
  }

  test("PATCH / should return 200 on created") {
    when(
      writeService
        .updateConcept(eqTo(1.toLong), any[UpdatedConcept]))
      .thenReturn(Success(TestData.sampleConcept))

    patch("/test/1", write(TestData.updatedConcept), headers = Map("Authorization" -> TestData.authHeaderWithWriteRole)) {
      status should equal(200)
    }
  }
  test("PATCH / should return 403 if no write role") {
    when(user.getUser).thenReturn(TestData.userWithNoRoles)
    when(
      writeService
        .updateConcept(eqTo(1.toLong), any[UpdatedConcept]))
      .thenReturn(Success(TestData.sampleConcept))

    patch("/test/1", write(TestData.updatedConcept), headers = Map("Authorization" -> TestData.authHeaderWithWriteRole)) {
      status should equal(403)
    }
  }

}
