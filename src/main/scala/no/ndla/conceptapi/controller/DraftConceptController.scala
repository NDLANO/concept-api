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
import no.ndla.conceptapi.model.api.{Concept, ConceptSearchResult, DraftConceptSearchParams, NotFoundException}
import no.ndla.conceptapi.model.domain.{ConceptStatus, Language, SearchResult, Sort}
import no.ndla.conceptapi.model.search.DraftSearchSettings
import no.ndla.conceptapi.service.search.{DraftConceptSearchService, SearchConverterService}
import no.ndla.conceptapi.service.{ConverterService, ReadService, WriteService}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.Ok
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scala.util.{Failure, Success}

trait DraftConceptController {
  this: WriteService
    with ReadService
    with User
    with DraftConceptSearchService
    with SearchConverterService
    with DraftNdlaController
    with ConverterService =>
  val draftConceptController: DraftConceptController

  class DraftConceptController(implicit val swagger: Swagger)
      extends DraftNdlaControllerClass
      with SwaggerSupport
      with LazyLogging {
    protected implicit override val jsonFormats: Formats = DefaultFormats
    val applicationDescription = "This is the Api for concept drafts"

    private val statuss = Param[String]("STATUS", "Concept status")
    private val statusFilter = Param[Option[Seq[String]]](
      "status",
      s"""List of statuses to filter by.
         |A draft only needs to have one of the available statuses to appear in result (OR).
       """.stripMargin
    )

    private def scrollSearchOr(scrollId: Option[String], language: String)(orFunction: => Any): Any =
      scrollId match {
        case Some(scroll) =>
          draftConceptSearchService.scroll(scroll, language) match {
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
        tagsToFilterBy: Set[String],
        statusFilter: Set[String]
    ) = {
      val settings = DraftSearchSettings(
        withIdIn = idList,
        searchLanguage = language,
        page = page,
        pageSize = pageSize,
        sort = sort.getOrElse(Sort.ByRelevanceDesc),
        fallback = fallback,
        subjects = subjects,
        tagsToFilterBy = tagsToFilterBy,
        statusFilter = statusFilter
      )

      val result = query match {
        case Some(q) =>
          draftConceptSearchService.matchingQuery(q, settings.copy(sort = sort.getOrElse(Sort.ByRelevanceDesc)))
        case None => draftConceptSearchService.all(settings.copy(sort = sort.getOrElse(Sort.ByTitleDesc)))
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
            asQueryParam(pageNo),
            asQueryParam(pageSize),
            asQueryParam(sort),
            asQueryParam(fallback),
            asQueryParam(scrollId),
            asQueryParam(subjects),
            asQueryParam(tagsToFilterBy),
            asQueryParam(statusFilter)
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
        val statusesToFilterBy = paramAsListOfString(this.statusFilter.paramName)

        search(
          query,
          sort,
          language,
          page,
          pageSize,
          idList,
          fallback,
          subjects.toSet,
          tagsToFilterBy.toSet,
          statusesToFilterBy.toSet
        )

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
            bodyParam[DraftConceptSearchParams]
        )
          authorizations "oauth2"
          responseMessages (response400, response500))
    ) {
      val body = extract[DraftConceptSearchParams](request.body)
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
            val statusFilter = searchParams.status

            search(
              query,
              sort,
              language,
              page,
              pageSize,
              idList,
              fallback,
              subjects,
              tagsToFilterBy,
              statusFilter
            )
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
          case Some(language) => writeService.deleteLanguage(id, language, userInfo)
          case None           => Failure(NotFoundException("Language not found"))
        }
      }
    }

    put(
      "/:concept_id/status/:STATUS",
      operation(
        apiOperation[Concept]("updateConceptStatus")
          summary "Update status of a concept"
          description "Update status of a concept"
          parameters (
            asPathParam(conceptId),
            asPathParam(statuss)
        )
          authorizations "oauth2"
          responseMessages (response400, response403, response404, response500))
    ) {
      val userInfo = user.getUser
      doOrAccessDenied(userInfo.canWrite) {
        val id = long(this.conceptId.paramName)

        ConceptStatus
          .valueOfOrError(params(this.statuss.paramName))
          .flatMap(writeService.updateConceptStatus(_, id, userInfo)) match {
          case Success(concept) => concept
          case Failure(ex)      => errorHandler(ex)
        }

      }
    }

    get(
      "/status-state-machine/",
      operation(
        apiOperation[Map[String, List[String]]]("getStatusStateMachine")
          summary "Get status state machine"
          description "Get status state machine"
          authorizations "oauth2"
          responseMessages response500
      )
    ) {
      val userInfo = user.getUser
      doOrAccessDenied(userInfo.canWrite) {
        converterService.stateTransitionsToApi(user.getUser)
      }
    }
  }
}
