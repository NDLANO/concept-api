/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.controller

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.ConceptApiProperties
import no.ndla.conceptapi.auth.User
import no.ndla.conceptapi.model.api.{
  Concept,
  ConceptSearchParams,
  ConceptSearchResult,
  NewConcept,
  NotFoundException,
  SubjectTags,
  TagsSearchResult,
  UpdatedConcept
}
import no.ndla.conceptapi.model.domain.{Language, SearchResult, Sort}
import no.ndla.conceptapi.model.search.SearchSettings
import no.ndla.conceptapi.service.search.{ConceptSearchService, PublishedConceptSearchService, SearchConverterService}
import no.ndla.conceptapi.service.{ReadService, WriteService}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{Created, Ok}
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scala.util.{Failure, Success}

trait PublishedConceptController {
  this: WriteService
    with ReadService
    with User
    with DraftConceptController
    with PublishedConceptSearchService
    with ConceptSearchService
    with SearchConverterService =>
  val publishedConceptController: PublishedConceptController

  class PublishedConceptController(implicit val swagger: Swagger)
      extends NdlaController
      with SwaggerSupport
      with LazyLogging {
    protected implicit override val jsonFormats: Formats = DefaultFormats

    val applicationDescription = "This is the Api for concepts"

    private def scrollSearchOr(scrollId: Option[String], language: String)(orFunction: => Any): Any =
      scrollId match {
        case Some(scroll) =>
          publishedConceptSearchService.scroll(scroll, language) match {
            case Success(scrollResult) =>
              Ok(searchConverterService.asApiConceptSearchResult(scrollResult), getResponseScrollHeader(scrollResult))
            case Failure(ex) => errorHandler(ex)
          }
        case None => orFunction
      }

    private def getResponseScrollHeader(result: SearchResult[_]) =
      result.scrollId.map(i => this.scrollId.paramName -> i).toMap

    private def search(
        query: Option[String],
        sort: Option[Sort.Value],
        language: String,
        page: Int,
        pageSize: Int,
        idList: List[Long],
        fallback: Boolean,
        subjects: Set[String],
        tagsToFilterBy: Set[String]
    ) = {
      val settings = SearchSettings(
        withIdIn = idList,
        searchLanguage = language,
        page = page,
        pageSize = pageSize,
        sort = sort.getOrElse(Sort.ByRelevanceDesc),
        fallback = fallback,
        subjects = subjects,
        tagsToFilterBy = tagsToFilterBy
      )

      val result = query match {
        case Some(q) =>
          publishedConceptSearchService.matchingQuery(q, settings.copy(sort = sort.getOrElse(Sort.ByRelevanceDesc)))
        case None => publishedConceptSearchService.all(settings.copy(sort = sort.getOrElse(Sort.ByTitleDesc)))
      }

      result match {
        case Success(searchResult) =>
          Ok(searchConverterService.asApiConceptSearchResult(searchResult), getResponseScrollHeader(searchResult))
        case Failure(ex) => errorHandler(ex)
      }

    }

    get(
      "/:concept_id",
      operation(
        apiOperation[String]("getConceptById")
          summary "Show concept with a specified id"
          description "Shows the concept for the specified id."
          parameters (
            asHeaderParam(correlationId),
            asQueryParam(language),
            asPathParam(conceptId),
            asQueryParam(fallback)
        )
          authorizations "oauth2"
          responseMessages (response404, response500))
    ) {
      val conceptId = long(this.conceptId.paramName)
      val language =
        paramOrDefault(this.language.paramName, Language.AllLanguages)
      val fallback = booleanOrDefault(this.fallback.paramName, false)

      readService.publishedConceptWithId(conceptId, language, fallback) match {
        case Success(concept) => Ok(concept)
        case Failure(ex)      => errorHandler(ex)
      }
    }

    get(
      "/",
      operation(
        apiOperation[ConceptSearchResult]("getAllConcepts")
          summary "Show all concepts"
          description "Shows all concepts. You can search it too."
          parameters (
            asHeaderParam(correlationId),
            asQueryParam(query),
            asQueryParam(conceptIds),
            asQueryParam(language),
            asQueryParam(pageNo),
            asQueryParam(pageSize),
            asQueryParam(sort),
            asQueryParam(fallback),
            asQueryParam(scrollId),
            asQueryParam(subjects)
        )
          authorizations "oauth2"
          responseMessages response500)
    ) {
      val language = paramOrDefault(this.language.paramName, Language.AllLanguages)
      val scrollId = paramOrNone(this.scrollId.paramName)

      scrollSearchOr(scrollId, language) {
        val query = paramOrNone(this.query.paramName)
        val sort = paramOrNone(this.sort.paramName).flatMap(Sort.valueOf)
        val pageSize = intOrDefault(this.pageSize.paramName, ConceptApiProperties.DefaultPageSize)
        val page = intOrDefault(this.pageNo.paramName, 1)
        val idList = paramAsListOfLong(this.conceptIds.paramName)
        val fallback = booleanOrDefault(this.fallback.paramName, default = false)
        val subjects = paramAsListOfString(this.subjects.paramName)
        val tagsToFilterBy = paramAsListOfString(this.tagsToFilterBy.paramName)

        search(query, sort, language, page, pageSize, idList, fallback, subjects.toSet, tagsToFilterBy.toSet)

      }
    }

    post(
      "/search/",
      operation(
        apiOperation[ConceptSearchResult]("searchConcepts")
          summary "Show all concepts"
          description "Shows all concepts. You can search it too."
          parameters (
            asHeaderParam(correlationId),
            bodyParam[ConceptSearchParams]
        )
          authorizations "oauth2"
          responseMessages (response400, response500))
    ) {
      val body = extract[ConceptSearchParams](request.body)
      val scrollId = body.map(_.scrollId).getOrElse(None)
      val lang = body.map(_.language).toOption.flatten

      scrollSearchOr(scrollId, lang.getOrElse(Language.DefaultLanguage)) {
        body match {
          case Success(searchParams) =>
            val query = searchParams.query
            val sort = searchParams.sort.flatMap(Sort.valueOf)
            val language = searchParams.language.getOrElse(Language.AllLanguages)
            val pageSize = searchParams.pageSize.getOrElse(ConceptApiProperties.DefaultPageSize)
            val page = searchParams.page.getOrElse(1)
            val idList = searchParams.idList
            val fallback = searchParams.fallback.getOrElse(false)
            val subjects = searchParams.subjects
            val tagsToFilterBy = searchParams.tags

            search(query, sort, language, page, pageSize, idList, fallback, subjects, tagsToFilterBy)
          case Failure(ex) => errorHandler(ex)
        }
      }
    }

    get( // TODO: Remove this endpoint from published controller when frontend starts using [[DraftConceptController]] version
      "/tags/",
      operation(
        apiOperation[List[SubjectTags]]("getTags")
          summary "Returns a list of all tags in the specified subjects"
          description "Returns a list of all tags in the specified subjects"
          parameters (
            asHeaderParam(correlationId),
            asQueryParam(language),
            asQueryParam(fallback),
            asQueryParam(subjects)
        )
          authorizations "oauth2"
          responseMessages (response400, response403, response404, response500))
    ) {
      val subjects = paramAsListOfString(this.subjects.paramName)
      val language = paramOrDefault(this.language.paramName, Language.AllLanguages)
      val fallback = booleanOrDefault(this.fallback.paramName, default = false)

      if (subjects.nonEmpty) {
        conceptSearchService.getTagsWithSubjects(subjects, language, fallback) match {
          case Success(res) if res.nonEmpty => Ok(res)
          case Success(res)                 => errorHandler(NotFoundException("Could not find any tags in the specified subjects"))
          case Failure(ex)                  => errorHandler(ex)
        }
      } else {
        readService.allTagsFromConcepts(language, fallback)
      }
    }

    get( // TODO: Remove this endpoint from published controller when frontend starts using [[DraftConceptController]] version
      "/tag-search/",
      operation(
        apiOperation[TagsSearchResult]("getTags-paginated")
          summary "Retrieves a list of all previously used tags in concepts"
          description "Retrieves a list of all previously used tags in concepts"
          parameters (
            asHeaderParam(correlationId),
            asQueryParam(query),
            asQueryParam(pageSize),
            asQueryParam(pageNo),
            asQueryParam(language)
        )
          responseMessages response500
          authorizations "oauth2")
    ) {

      val query = paramOrDefault(this.query.paramName, "")
      val pageSize = intOrDefault(this.pageSize.paramName, ConceptApiProperties.DefaultPageSize) match {
        case tooSmall if tooSmall < 1 => ConceptApiProperties.DefaultPageSize
        case x                        => x
      }
      val pageNo = intOrDefault(this.pageNo.paramName, 1) match {
        case tooSmall if tooSmall < 1 => 1
        case x                        => x
      }
      val language = paramOrDefault(this.language.paramName, Language.AllLanguages)

      readService.getAllTags(query, pageSize, pageNo, language)
    }

    get( // TODO: Remove this endpoint from published controller when frontend starts using [[DraftConceptController]] version
      "/subjects/",
      operation(
        apiOperation[List[String]]("getSubjects")
          summary "Returns a list of all subjects used in concepts"
          description "Returns a list of all subjects used in concepts"
          parameters (
            asHeaderParam(correlationId)
          )
          authorizations "oauth2"
          responseMessages (response400, response403, response404, response500))
    ) {
      readService.allSubjects() match {
        case Success(subjects) => Ok(subjects)
        case Failure(ex)       => errorHandler(ex)
      }
    }

    post( // TODO: Remove this endpoint from published controller when frontend starts using [[DraftConceptController]] version
      "/",
      operation(
        apiOperation[Concept]("newConceptById")
          summary "Create new concept"
          description "Create new concept"
          parameters (
            asHeaderParam(correlationId),
            bodyParam[NewConcept]
        )
          authorizations "oauth2"
          responseMessages (response400, response403, response500))
    ) {
      doOrAccessDenied(user.getUser.canWrite) {
        val body = extract[NewConcept](request.body)
        body.flatMap(writeService.newConcept) match {
          case Success(c)  => Created(c)
          case Failure(ex) => errorHandler(ex)
        }
      }
    }

    patch( // TODO: Remove this endpoint from published controller when frontend starts using [[DraftConceptController]] version
      "/:concept_id",
      operation(
        apiOperation[Concept]("updateConceptById")
          summary "Update a concept"
          description "Update a concept"
          parameters (
            asHeaderParam(correlationId),
            bodyParam[UpdatedConcept],
            asPathParam(conceptId)
        )
          authorizations "oauth2"
          responseMessages (response400, response403, response404, response500))
    ) {
      val userInfo = user.getUser
      doOrAccessDenied(userInfo.canWrite) {
        val body = extract[UpdatedConcept](request.body)
        val conceptId = long(this.conceptId.paramName)
        body.flatMap(writeService.updateConcept(conceptId, _, userInfo)) match {
          case Success(c)  => Ok(c)
          case Failure(ex) => errorHandler(ex)
        }
      }
    }
  }
}
