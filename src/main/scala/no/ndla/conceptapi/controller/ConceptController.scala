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
  Error,
  NewConcept,
  NotFoundException,
  UpdatedConcept,
  ValidationError
}
import no.ndla.conceptapi.model.domain.{Language, SearchResult, Sort}
import no.ndla.conceptapi.model.search.SearchSettings
import no.ndla.conceptapi.service.search.{ConceptSearchService, SearchConverterService}
import no.ndla.conceptapi.service.{ReadService, WriteService}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.swagger.{ResponseMessage, Swagger, SwaggerSupport}
import org.scalatra.{Created, Ok}

import scala.util.{Failure, Success}

trait ConceptController {
  this: WriteService with ReadService with User with ConceptSearchService with SearchConverterService =>
  val conceptController: ConceptController

  class ConceptController(implicit val swagger: Swagger) extends NdlaController with SwaggerSupport with LazyLogging {
    protected implicit override val jsonFormats: Formats = DefaultFormats
    private val conceptId =
      Param[Long]("concept_id", "Id of the concept that is to be returned")

    val applicationDescription = "This is the Api for concepts"

    // Additional models used in error responses
    registerModel[ValidationError]()
    registerModel[Error]()

    val response400 =
      ResponseMessage(400, "Validation Error", Some("ValidationError"))
    val response403 = ResponseMessage(403, "Access Denied", Some("Error"))
    val response404 = ResponseMessage(404, "Not found", Some("Error"))
    val response500 = ResponseMessage(500, "Unknown error", Some("Error"))

    private val query =
      Param[Option[String]]("query", "Return only concepts with content matching the specified query.")
    private val conceptIds = Param[Option[Seq[Long]]](
      "ids",
      "Return only concepts that have one of the provided ids. To provide multiple ids, separate by comma (,).")

    private def scrollSearchOr(scrollId: Option[String], language: String)(orFunction: => Any): Any =
      scrollId match {
        case Some(scroll) =>
          conceptSearchService.scroll(scroll, language) match {
            case Success(scrollResult) =>
              Ok(searchConverterService.asApiConceptSearchResult(scrollResult), getResponseScrollHeader(scrollResult))
            case Failure(ex) => errorHandler(ex)
          }
        case None => orFunction
      }

    private def getResponseScrollHeader(result: SearchResult[_]) =
      result.scrollId.map(i => this.scrollId.paramName -> i).toMap

    private def search(query: Option[String],
                       sort: Option[Sort.Value],
                       language: String,
                       page: Int,
                       pageSize: Int,
                       idList: List[Long],
                       fallback: Boolean) = {

      val settings = SearchSettings(
        withIdIn = idList,
        searchLanguage = language,
        page = page,
        pageSize = pageSize,
        sort = sort.getOrElse(Sort.ByRelevanceDesc),
        fallback = fallback
      )

      val result = query match {
        case Some(q) => conceptSearchService.matchingQuery(q, settings)
        case None    => conceptSearchService.all(settings)
      }

      result match {
        case Success(searchResult) =>
          Ok(searchConverterService.asApiConceptSearchResult(searchResult), getResponseScrollHeader(searchResult))
        case Failure(ex) => errorHandler(ex)
      }

    }

    post(
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

    patch(
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
      doOrAccessDenied(user.getUser.canWrite) {
        val body = extract[UpdatedConcept](request.body)
        val conceptId = long(this.conceptId.paramName)
        body.flatMap(writeService.updateConcept(conceptId, _)) match {
          case Success(c)  => Ok(c)
          case Failure(ex) => errorHandler(ex)
        }
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

      readService.conceptWithId(conceptId, language, fallback) match {
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
            asQueryParam(query),
            asQueryParam(pageNo),
            asQueryParam(pageSize),
            asQueryParam(sort),
            asQueryParam(fallback),
            asQueryParam(scrollId)
        )
          authorizations "oauth2"
          responseMessages response500)
    ) {
      val language = paramOrDefault(this.language.paramName, Language.AllLanguages)
      val scrollId = paramOrNone(this.scrollId.paramName)

      scrollSearchOr(scrollId, language) {
        val query = paramOrNone(this.query.paramName)
        val sort = Sort.valueOf(paramOrDefault(this.sort.paramName, "title"))
        val pageSize = intOrDefault(this.pageSize.paramName, ConceptApiProperties.DefaultPageSize)
        val page = intOrDefault(this.pageNo.paramName, 1)
        val idList = paramAsListOfLong(this.conceptIds.paramName)
        val fallback = booleanOrDefault(this.fallback.paramName, default = false)

        search(query, sort, language, page, pageSize, idList, fallback)

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
            val sort = Sort.valueOf(searchParams.sort.getOrElse("title"))
            val language = searchParams.language.getOrElse(Language.AllLanguages)
            val pageSize = searchParams.pageSize.getOrElse(ConceptApiProperties.DefaultPageSize)
            val page = searchParams.page.getOrElse(1)
            val idList = searchParams.idList
            val fallback = searchParams.fallback.getOrElse(false)

            search(query, sort, language, page, pageSize, idList, fallback)
          case Failure(ex) => errorHandler(ex)
        }
      }
    }

    delete(
      "/:concept_id",
      operation(
        apiOperation[Concept]("deleteLanguage")
          summary "Delete language from concept"
          description "Delete language from concept"
          parameters (
            asHeaderParam(correlationId),
            asPathParam(conceptId),
            asQueryParam(pathLanguage)
        )
          authorizations "oauth2"
          responseMessages (response400, response403, response404, response500))
    ) {
      val userInfo = user.getUser
      val language = paramOrNone(this.language.paramName)
      doOrAccessDenied(userInfo.canWrite) {
        val id = long(this.conceptId.paramName)
        language match {
          case Some(language) => writeService.deleteLanguage(id, language)
          case None           => Failure(NotFoundException("Language not found"))
        }
      }
    }

  }
}
