/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import java.util.Date

import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.{TestData, TestEnvironment}
import no.ndla.conceptapi.UnitSuite

import scala.util.{Failure, Success}

class ConverterServiceTest extends UnitSuite with TestEnvironment {

  val service = new ConverterService
  val userInfo = TestData.userWithWriteAccess.copy(id = "")

  test("toApiConcept converts all fields correctly") {
    val today = new Date()
    when(clock.now()).thenReturn(today)

    val domainConcept = TestData.emptyDomainConcept.copy(
      id = Some(1),
      revision = Some(1),
      title = Seq(domain.ConceptTitle("Some title", "en")),
      content = Seq(domain.ConceptContent("Some content", "en")),
      copyright = Some(
        domain.Copyright(
          license = None,
          origin = Some("New Origin"),
          creators = Seq(domain.Author("originator", "New Creator name")),
          processors = Seq(domain.Author("writer", "New Processor name")),
          rightsholders = Seq(domain.Author("rightsholder", "New Rightholder name")),
          agreementId = Some(1),
          validFrom = Some(today),
          validTo = Some(today)
        )),
      source = Some("https://www.some.url"),
      created = today,
      updated = today,
      updatedBy = Seq("new user"),
      metaImage = Seq.empty,
      tags = Seq(domain.ConceptTags(Seq("Tag1", "Tag2"), "en")),
      subjectIds = Set("subj1", "subj2"),
      articleId = Some(42),
      status = domain.Status.default
    )
    val apiConcept = Success(
      TestData.emptyApiConcept.copy(
        id = 1,
        revision = 1,
        title = Some(api.ConceptTitle(title = "Some title", language = "en")),
        content = Some(api.ConceptContent(content = "Some content", language = "en")),
        copyright = Some(api.Copyright(
          license = None,
          origin = Some("New Origin"),
          creators = Seq(api.Author("originator", "New Creator name")),
          processors = Seq(api.Author("writer", "New Processor name")),
          rightsholders = Seq(api.Author("rightsholder", "New Rightholder name")),
          agreementId = Some(1),
          validFrom = Some(today),
          validTo = Some(today)
        )),
        source = Some("https://www.some.url"),
        metaImage = Some(api.ConceptMetaImage("", "", "unknown")),
        tags = Some(api.ConceptTags(Seq("Tag1", "Tag2"), "en")),
        subjectIds = Some(Set("subj1", "subj2")),
        created = today,
        updated = today,
        updatedBy = Some(Seq("new user")),
        supportedLanguages = Set("en"),
        articleId = Some(42),
        status = api.Status("DRAFT", Seq.empty)
      ))

    service.toApiConcept(domainConcept, language = "en", fallback = false) should be(apiConcept)
  }

  test("toApiConcept fails if concept is not found in specified language without fallback") {
    val domainConcept = TestData.emptyDomainConcept.copy(id = Some(1))
    service.toApiConcept(domainConcept, "hei", fallback = false) should be(
      Failure(
        api.NotFoundException(s"The concept with id ${domainConcept.id.get} and language 'hei' was not found.",
                              domainConcept.supportedLanguages.toSeq))
    )
  }

  test("toApiConcept fails if domainConcept.id is None") {
    val domainConcept = TestData.emptyDomainConcept.copy(id = None)
    service.toApiConcept(domainConcept, "nb", fallback = true) should be(
      Failure(
        api.ConceptMissingIdException("Missing id when converting domain-concept to api-concept. This is a bug")
      ))
  }

  test("toApiConcept successes if concept is not found in specified language, but with fallback") {
    val today = new Date()
    when(clock.now()).thenReturn(today)

    val domainConcept =
      TestData.emptyDomainConcept.copy(id = Some(1),
                                       revision = Some(1),
                                       created = today,
                                       updated = today,
                                       updatedBy = Seq.empty,
                                       status = domain.Status.default)
    val apiConcept = Success(
      TestData.emptyApiConcept.copy(
        id = 1,
        revision = 1,
        title = Some(api.ConceptTitle(title = "", language = "unknown")),
        content = Some(api.ConceptContent(content = "", language = "unknown")),
        metaImage = Some(api.ConceptMetaImage(url = "", alt = "", language = "unknown")),
        created = today,
        updated = today,
        status = api.Status("DRAFT", Seq.empty)
      ))

    service.toApiConcept(domainConcept, "hei", fallback = true) should be(apiConcept)
  }

  test("toDomainConcept-newConcept updates all fields with new entry on create") {
    val today = new Date()
    when(clock.now()).thenReturn(today)

    val newApiConcept = TestData.emptyApiNewConcept.copy(
      language = "en",
      title = "Some title",
      content = Some("Some content"),
      copyright = Some(
        api.Copyright(
          license = Some(api
            .License(license = "New License", description = Some("Some new license"), url = Some("http://some.url"))),
          origin = Some("New Origin"),
          creators = Seq(api.Author("originator", "New Creator name")),
          processors = Seq(api.Author("writer", "New Processor name")),
          rightsholders = Seq(api.Author("rightsholder", "New Rightholder name")),
          agreementId = Some(1),
          validFrom = Some(today),
          validTo = Some(today)
        )),
      source = Some("https://www.some.url"),
      metaImage = Some(api.NewConceptMetaImage("1", "Alt text")),
      tags = Some(Seq("Tag1", "Tag2")),
      subjectIds = Some(Seq("subj1", "subj2")),
      articleId = Some(42)
    )
    val newUser = userInfo.copy(id = "new user")
    val newDomainConcept = TestData.emptyDomainConcept.copy(
      id = None,
      revision = None,
      title = Seq(domain.ConceptTitle("Some title", "en")),
      content = Seq(domain.ConceptContent("Some content", "en")),
      copyright = Some(
        domain.Copyright(
          license = Some("New License"),
          origin = Some("New Origin"),
          creators = Seq(domain.Author("originator", "New Creator name")),
          processors = Seq(domain.Author("writer", "New Processor name")),
          rightsholders = Seq(domain.Author("rightsholder", "New Rightholder name")),
          agreementId = Some(1),
          validFrom = Some(today),
          validTo = Some(today)
        )),
      source = Some("https://www.some.url"),
      created = today,
      updated = today,
      updatedBy = Seq("new user"),
      metaImage = Seq(domain.ConceptMetaImage("1", "Alt text", "en")),
      tags = Seq(domain.ConceptTags(Seq("Tag1", "Tag2"), "en")),
      subjectIds = Set("subj1", "subj2"),
      articleId = Some(42),
      status = domain.Status.default
    )

    service.toDomainConcept(newApiConcept, newUser) should be(Success(newDomainConcept))
  }

  test("toDomainConcept-updateConcept adds and updates title correctly") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val conceptWithTitle = TestData.emptyDomainConcept.copy(
      title = Seq(domain.ConceptTitle("Bokmål tittel", "nb")),
    )
    val newTitle = TestData.emptyApiUpdatedConcept.copy(
      language = "nn",
      title = Some("Nynorsk tittel"),
    )
    val updatedTitle = TestData.emptyApiUpdatedConcept.copy(
      language = "nb",
      title = Some("Bokmål tittel oppdatert"),
    )
    val conceptWithNewTitle = TestData.emptyDomainConcept.copy(
      title = Seq(
        domain.ConceptTitle("Bokmål tittel", "nb"),
        domain.ConceptTitle("Nynorsk tittel", "nn")
      ),
      updated = updated
    )
    val conceptWithUpdatedTitle = TestData.emptyDomainConcept.copy(
      title = Seq(
        domain.ConceptTitle("Bokmål tittel oppdatert", "nb"),
      ),
      updated = updated
    )

    service.toDomainConcept(conceptWithTitle, newTitle, userInfo) should be(conceptWithNewTitle)
    service.toDomainConcept(conceptWithTitle, updatedTitle, userInfo) should be(conceptWithUpdatedTitle)
  }

  test("toDomainConcept-updateConcept adds and updates content correctly") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val conceptWithContent = TestData.emptyDomainConcept.copy(
      content = Seq(domain.ConceptContent("Innhold", "nb"))
    )
    val newContent = TestData.emptyApiUpdatedConcept.copy(
      language = "nn",
      content = Some("Nytt innhald")
    )
    val updatedContent = TestData.emptyApiUpdatedConcept.copy(
      language = "nb",
      content = Some("Bokmål innhold oppdatert")
    )
    val conceptWithNewContent = TestData.emptyDomainConcept.copy(
      content = Seq(
        domain.ConceptContent("Innhold", "nb"),
        domain.ConceptContent("Nytt innhald", "nn")
      ),
      updated = updated
    )
    val conceptWithUpdatedContent = TestData.emptyDomainConcept.copy(
      content = Seq(
        domain.ConceptContent("Bokmål innhold oppdatert", "nb"),
      ),
      updated = updated
    )

    service.toDomainConcept(conceptWithContent, newContent, userInfo) should be(conceptWithNewContent)
    service.toDomainConcept(conceptWithContent, updatedContent, userInfo) should be(conceptWithUpdatedContent)
  }

  test("toDomainConcept-updateConcept adds copyright correctly") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val conceptWithoutCopyright = TestData.emptyDomainConcept.copy(
      copyright = None
    )
    val newCopyright = TestData.emptyApiUpdatedConcept.copy(
      copyright = Some(api.Copyright(
        license = Some(
          api.License(license = "New License", description = Some("Some new license"), url = Some("http://some.url"))),
        origin = Some("New Origin"),
        creators = Seq(api.Author("originator", "New Creator name")),
        processors = Seq(api.Author("writer", "New Processor name")),
        rightsholders = Seq(api.Author("rightsholder", "New Rightholder name")),
        agreementId = Some(1),
        validFrom = Some(updated),
        validTo = Some(updated)
      )))
    val conceptWithNewCopyright = TestData.emptyDomainConcept.copy(
      copyright = Some(
        domain.Copyright(
          license = Some("New License"),
          origin = Some("New Origin"),
          creators = Seq(domain.Author("originator", "New Creator name")),
          processors = Seq(domain.Author("writer", "New Processor name")),
          rightsholders = Seq(domain.Author("rightsholder", "New Rightholder name")),
          agreementId = Some(1),
          validFrom = Some(updated),
          validTo = Some(updated)
        )),
      updated = updated
    )

    service.toDomainConcept(conceptWithoutCopyright, newCopyright, userInfo) should be(conceptWithNewCopyright)
  }

  test("toDomainConcept-updateConcept updates copyright correctly") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val conceptWithCopyright = TestData.emptyDomainConcept.copy(
      copyright = Some(domain.Copyright(
        license = Some("License"),
        origin = Some("Origin"),
        creators = Seq(domain.Author("photographer", "Creator name")),
        processors = Seq(domain.Author("artist", "Processor name")),
        rightsholders = Seq(domain.Author("editorial", "Rightholder name")),
        agreementId = Some(1),
        validFrom = Some(updated),
        validTo = Some(updated)
      )))
    val updatedCopyright = TestData.emptyApiUpdatedConcept.copy(
      copyright = Some(
        api.Copyright(
          license = Some(
            api.License(license = "Updated License",
                        description = Some("Updated license"),
                        url = Some("http://updated.url"))),
          origin = Some("Updated Origin"),
          creators = Seq(api.Author("reader", "Updated Creator name")),
          processors = Seq(api.Author("translator", "Updated Processor name")),
          rightsholders = Seq(api.Author("director", "Updated Rightholder name")),
          agreementId = Some(1),
          validFrom = Some(updated),
          validTo = Some(updated)
        ))
    )
    val conceptWithUpdatedCopyright = TestData.emptyDomainConcept.copy(
      copyright = Some(
        domain.Copyright(
          license = Some("Updated License"),
          origin = Some("Updated Origin"),
          creators = Seq(domain.Author("reader", "Updated Creator name")),
          processors = Seq(domain.Author("translator", "Updated Processor name")),
          rightsholders = Seq(domain.Author("director", "Updated Rightholder name")),
          agreementId = Some(1),
          validFrom = Some(updated),
          validTo = Some(updated)
        )),
      updated = updated
    )

    service.toDomainConcept(conceptWithCopyright, updatedCopyright, userInfo) should be(conceptWithUpdatedCopyright)
  }

  test("toDomainConcept-updateConcept updates updatedBy with new entry from userToken") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val conceptWithoutUpdatedBy = TestData.emptyDomainConcept.copy(
      updatedBy = Seq.empty,
      updated = updated
    )
    val emptyApiNewConcept = TestData.emptyApiUpdatedConcept
    val newUpdatedBy = userInfo.copy(id = "new updatedBy")
    val conceptWithUpdatedUpdatedBy = TestData.emptyDomainConcept.copy(
      updatedBy = Seq("new updatedBy"),
      updated = updated
    )

    service.toDomainConcept(conceptWithoutUpdatedBy, emptyApiNewConcept, newUpdatedBy) should be(
      conceptWithUpdatedUpdatedBy)
  }

  test("toDomainConcept-updateConcept does not produce duplicates in updatedBy") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val beforeUpdate = TestData.emptyDomainConcept.copy(
      updatedBy = Seq("test1", "test2"),
      updated = updated
    )
    val emptyApiNewConcept = TestData.emptyApiUpdatedConcept
    val updateWith = userInfo.copy(id = "test1")
    val afterUpdate = TestData.emptyDomainConcept.copy(
      updatedBy = Seq("test1", "test2"),
      updated = updated
    )

    service.toDomainConcept(beforeUpdate, emptyApiNewConcept, updateWith) should be(afterUpdate)
  }

  test("toDomainConcept-updateConcept deletes metaImage when getting null as a parameter") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val beforeUpdate = TestData.emptyDomainConcept.copy(
      metaImage = Seq(domain.ConceptMetaImage("1", "Hei", "nb"), domain.ConceptMetaImage("2", "Hej", "nn")),
      updated = updated
    )
    val updateWith = TestData.emptyApiUpdatedConcept.copy(language = "nb", metaImage = Left(null))
    val afterUpdate = TestData.emptyDomainConcept.copy(
      metaImage = Seq(domain.ConceptMetaImage("2", "Hej", "nn")),
      updated = updated
    )

    service.toDomainConcept(beforeUpdate, updateWith, userInfo) should be(afterUpdate)
  }

  test("toDomainConcept-updateConcept updates metaImage when getting new metaImage as a parameter") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val beforeUpdate = TestData.emptyDomainConcept.copy(
      metaImage = Seq(domain.ConceptMetaImage("1", "Hei", "nb"), domain.ConceptMetaImage("2", "Hej", "nn")),
      updated = updated
    )
    val updateWith = TestData.emptyApiUpdatedConcept.copy(language = "nb",
                                                          metaImage = Right(Some(api.NewConceptMetaImage("1", "Hola"))))
    val afterUpdate = TestData.emptyDomainConcept.copy(
      metaImage = Seq(domain.ConceptMetaImage("2", "Hej", "nn"), domain.ConceptMetaImage("1", "Hola", "nb")),
      updated = updated
    )

    service.toDomainConcept(beforeUpdate, updateWith, userInfo) should be(afterUpdate)
  }

  test("toDomainConcept-updateConcept does nothing to metaImage when getting None as a parameter") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val beforeUpdate = TestData.emptyDomainConcept.copy(
      metaImage = Seq(domain.ConceptMetaImage("1", "Hei", "nb"), domain.ConceptMetaImage("2", "Hej", "nn")),
      updated = updated
    )
    val updateWith = TestData.emptyApiUpdatedConcept.copy(language = "nb", metaImage = Right(None))
    val afterUpdate = TestData.emptyDomainConcept.copy(
      metaImage = Seq(domain.ConceptMetaImage("1", "Hei", "nb"), domain.ConceptMetaImage("2", "Hej", "nn")),
      updated = updated
    )

    service.toDomainConcept(beforeUpdate, updateWith, userInfo) should be(afterUpdate)
  }

  test("toDomainConcept-updateConcept adds and updates tags correctly") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val conceptWithTags = TestData.emptyDomainConcept.copy(
      tags = Seq(domain.ConceptTags(Seq("Tag1", "Tag2"), "nb"))
    )
    val newTag = TestData.emptyApiUpdatedConcept.copy(
      language = "nn",
      tags = Some(Seq("Tag3"))
    )
    val updatedTags = TestData.emptyApiUpdatedConcept.copy(
      language = "nb",
      tags = Some(Seq("Tag3", "Tag4"))
    )
    val NoneTags = TestData.emptyApiUpdatedConcept.copy(
      language = "nb",
      tags = None
    )
    val conceptWithNewTag = TestData.emptyDomainConcept.copy(
      tags = Seq(
        domain.ConceptTags(Seq("Tag1", "Tag2"), "nb"),
        domain.ConceptTags(Seq("Tag3"), "nn")
      ),
      updated = updated
    )
    val conceptWithUpdatedTag = TestData.emptyDomainConcept.copy(
      tags = Seq(domain.ConceptTags(Seq("Tag3", "Tag4"), "nb")),
      updated = updated
    )
    val conceptWithUpdatedNoneTag = TestData.emptyDomainConcept.copy(
      tags = Seq(domain.ConceptTags(Seq("Tag1", "Tag2"), "nb")),
      updated = updated
    )

    service.toDomainConcept(conceptWithTags, newTag, userInfo) should be(conceptWithNewTag)
    service.toDomainConcept(conceptWithTags, updatedTags, userInfo) should be(conceptWithUpdatedTag)
    service.toDomainConcept(conceptWithTags, NoneTags, userInfo) should be(conceptWithUpdatedNoneTag)
  }

  test("toDomainConcept-updateConcept adds and updates subjectIds correctly") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val conceptWithSubjectIds = TestData.emptyDomainConcept.copy(
      subjectIds = Set("subject1", "subject2")
    )
    val newSubjectId = TestData.emptyApiUpdatedConcept.copy(
      subjectIds = Some(Seq("newSubject"))
    )
    val updatedSubjectIds = TestData.emptyApiUpdatedConcept.copy(
      subjectIds = Some(Seq("updatedSubject1", "updatedSubject2"))
    )
    val emptySubjectIds = TestData.emptyApiUpdatedConcept.copy(
      subjectIds = Some(Seq.empty)
    )
    val NoneSubjectIds = TestData.emptyApiUpdatedConcept.copy(
      subjectIds = None
    )
    val conceptWithNewSubjectIds = TestData.emptyDomainConcept.copy(
      subjectIds = Set("newSubject"),
      updated = updated
    )
    val conceptWithUpdatedSubjectIds = TestData.emptyDomainConcept.copy(
      subjectIds = Set("updatedSubject1", "updatedSubject2"),
      updated = updated
    )
    val conceptWithUpdatedEmptySubjectIds = TestData.emptyDomainConcept.copy(
      subjectIds = Set.empty,
      updated = updated
    )
    val conceptWithUpdatedNoneSubjectIds = TestData.emptyDomainConcept.copy(
      subjectIds = Set("subject1", "subject2"),
      updated = updated
    )

    service.toDomainConcept(conceptWithSubjectIds, newSubjectId, userInfo) should be(conceptWithNewSubjectIds)
    service.toDomainConcept(conceptWithSubjectIds, updatedSubjectIds, userInfo) should be(conceptWithUpdatedSubjectIds)
    service.toDomainConcept(conceptWithSubjectIds, emptySubjectIds, userInfo) should be(
      conceptWithUpdatedEmptySubjectIds)
    service.toDomainConcept(conceptWithSubjectIds, NoneSubjectIds, userInfo) should be(conceptWithUpdatedNoneSubjectIds)
  }

  test("toDomainConcept-updateConcept deletes articleId when getting null as a parameter") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val beforeUpdate = TestData.emptyDomainConcept.copy(
      articleId = Some(12),
    )
    val updateWith = TestData.emptyApiUpdatedConcept.copy(articleId = Left(null))
    val afterUpdate = TestData.emptyDomainConcept.copy(
      articleId = None,
      updated = updated
    )

    service.toDomainConcept(beforeUpdate, updateWith, userInfo) should be(afterUpdate)
  }

  test("toDomainConcept-updateConcept updates articleId when getting new articleId as a parameter") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val beforeUpdate = TestData.emptyDomainConcept.copy(
      articleId = None,
    )
    val updateWith = TestData.emptyApiUpdatedConcept.copy(articleId = Right(Some(12)))
    val afterUpdate = TestData.emptyDomainConcept.copy(
      articleId = Some(12),
      updated = updated
    )

    service.toDomainConcept(beforeUpdate, updateWith, userInfo) should be(afterUpdate)
  }

  test("toDomainConcept-updateConcept does nothing to articleId when getting None as a parameter") {
    val updated = new Date()
    when(clock.now()).thenReturn(updated)

    val beforeUpdate = TestData.emptyDomainConcept.copy(
      articleId = Some(12),
    )
    val updateWith = TestData.emptyApiUpdatedConcept.copy(articleId = Right(None))
    val afterUpdate = TestData.emptyDomainConcept.copy(
      articleId = Some(12),
      updated = updated
    )

    service.toDomainConcept(beforeUpdate, updateWith, userInfo) should be(afterUpdate)
  }

  test("toDomainConcept-updateConcept-with-ID updates articleId when getting new articleId as a parameter") {
    val today = new Date()
    when(clock.now()).thenReturn(today)

    val updateWith = TestData.emptyApiUpdatedConcept.copy(articleId = Right(Some(15)))
    val afterUpdate = TestData.emptyDomainConcept.copy(
      id = Some(12),
      articleId = Some(15),
      created = today,
      updated = today,
    )

    service.toDomainConcept(12, updateWith, userInfo) should be(
      afterUpdate
    )
  }

  test("toDomainConcept-updateConcept-with-ID sets articleId to None when articleId is not specified") {
    val today = new Date()
    when(clock.now()).thenReturn(today)

    val updateWith = TestData.emptyApiUpdatedConcept.copy(articleId = Left(null))
    val afterUpdate = TestData.emptyDomainConcept.copy(
      id = Some(12),
      articleId = None,
      created = today,
      updated = today,
    )

    service.toDomainConcept(12, updateWith, userInfo) should be(
      afterUpdate
    )
  }

  test("toDomainConcept-updateConcept-with-ID updates metaImage when getting new metaImage as a parameter") {
    val today = new Date()
    when(clock.now()).thenReturn(today)

    val updateWith = TestData.emptyApiUpdatedConcept.copy(language = "nb",
                                                          metaImage = Right(Some(api.NewConceptMetaImage("1", "Hola"))))
    val afterUpdate = TestData.emptyDomainConcept.copy(
      id = Some(12),
      metaImage = Seq(domain.ConceptMetaImage("1", "Hola", "nb")),
      created = today,
      updated = today,
    )

    service.toDomainConcept(12, updateWith, userInfo) should be(
      afterUpdate
    )
  }

  test("toDomainConcept-updateConcept-with-ID sets metaImage to Seq.empty when metaImage is not specified") {
    val today = new Date()
    when(clock.now()).thenReturn(today)

    val updateWith = TestData.emptyApiUpdatedConcept.copy(language = "nb", metaImage = Left(null))
    val afterUpdate = TestData.emptyDomainConcept.copy(
      id = Some(12),
      metaImage = Seq.empty,
      created = today,
      updated = today,
    )

    service.toDomainConcept(12, updateWith, userInfo) should be(
      afterUpdate
    )
  }

  test("toDomainConcept-updateConcept-with-ID updates updatedBy with new entry from userToken") {
    val today = new Date()
    when(clock.now()).thenReturn(today)

    val emptyApiUpdatedConcept = TestData.emptyApiUpdatedConcept
    val updateWith = userInfo.copy(id = "test")
    val afterUpdate = TestData.emptyDomainConcept.copy(
      id = Some(12),
      updatedBy = Seq("test"),
      created = today,
      updated = today,
    )

    service.toDomainConcept(12, emptyApiUpdatedConcept, updateWith) should be(afterUpdate)
  }

}
