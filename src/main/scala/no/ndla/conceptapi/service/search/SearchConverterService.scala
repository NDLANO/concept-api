/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service.search

import com.sksamuel.elastic4s.http.search.SearchHit
import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.model.api.{ConceptMetaImage, ConceptSearchResult}
import no.ndla.conceptapi.model.{api, domain}
import no.ndla.conceptapi.model.domain.Language.getSupportedLanguages
import no.ndla.conceptapi.model.domain.{Concept, Language, SearchResult}
import no.ndla.conceptapi.model.api
import no.ndla.conceptapi.model.search._
import no.ndla.conceptapi.service.ConverterService
import no.ndla.mapping.ISO639
import org.joda.time.DateTime
import org.json4s._
import org.json4s.native.Serialization.read
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities.EscapeMode

import scala.jdk.CollectionConverters._

trait SearchConverterService {
  this: ConverterService =>
  val searchConverterService: SearchConverterService

  class SearchConverterService extends LazyLogging {
    implicit val formats: Formats = SearchableLanguageFormats.JSonFormats

    private def parseHtml(html: String) = {
      val document = Jsoup.parseBodyFragment(html)
      document.outputSettings().escapeMode(EscapeMode.xhtml).prettyPrint(false)
      document.body()
    }

    private[service] def getEmbedResources(html: String): List[String] = {
      parseHtml(html)
        .select("embed")
        .asScala
        .flatMap(getEmbedResources)
        .toList
    }

    private def getEmbedResources(embed: Element): List[String] = {
      val attributesToKeep = List(
        "data-resource",
      )

      attributesToKeep.flatMap(attr =>
        embed.attr(attr) match {
          case "" => None
          case a  => Some(a)
      })
    }

    private[service] def getEmbedIds(html: String): List[String] = {
      parseHtml(html)
        .select("embed")
        .asScala
        .flatMap(getEmbedIds)
        .toList
    }

    private def getEmbedIds(embed: Element): List[String] = {
      val attributesToKeep = List(
        "data-videoid",
        "data-url",
        "data-resource_id",
        "data-content-id",
      )

      attributesToKeep.flatMap(attr =>
        embed.attr(attr) match {
          case "" => None
          case a  => Some(a)
      })
    }

    private def getEmbedResourcesToIndex(visualElement: Seq[domain.VisualElement]): SearchableLanguageList = {
      val visualElementTuples = visualElement.map(v => v.language -> getEmbedResources(v.visualElement))
      val attrsGroupedByLanguage = visualElementTuples.groupBy(_._1)

      val languageValues = attrsGroupedByLanguage.map {
        case (language, values) => LanguageValue(language, values.flatMap(_._2))
      }

      SearchableLanguageList(languageValues.toSeq)
    }

    private def getEmbedIdsToIndex(visualElement: Seq[domain.VisualElement],
                                   metaImage: Seq[domain.ConceptMetaImage]): SearchableLanguageList = {
      val visualElementTuples = visualElement.map(v => v.language -> getEmbedIds(v.visualElement))
      val metaImageTuples = metaImage.map(m => m.language -> List(m.imageId))
      val attrsGroupedByLanguage = (visualElementTuples ++ metaImageTuples).groupBy(_._1)

      val languageValues = attrsGroupedByLanguage.map {
        case (language, values) => LanguageValue(language, values.flatMap(_._2))
      }

      SearchableLanguageList(languageValues.toSeq)
    }

    def asSearchableConcept(c: Concept): SearchableConcept = {
      val defaultTitle = c.title
        .sortBy(title => {
          val languagePriority = Language.languageAnalyzers.map(la => la.lang).reverse
          languagePriority.indexOf(title.language)
        })
        .lastOption
      val embedResources = getEmbedResourcesToIndex(c.visualElement)
      val embedIds = getEmbedIdsToIndex(c.visualElement, c.metaImage)

      SearchableConcept(
        id = c.id.get,
        title = SearchableLanguageValues(c.title.map(title => LanguageValue(title.language, title.title))),
        content = SearchableLanguageValues(c.content.map(content => LanguageValue(content.language, content.content))),
        defaultTitle = defaultTitle.map(_.title),
        metaImage = c.metaImage,
        tags = SearchableLanguageList(c.tags.map(tag => LanguageValue(tag.language, tag.tags))),
        subjectIds = c.subjectIds.toSeq,
        lastUpdated = new DateTime(c.updated),
        status = Status(c.status.current.toString, c.status.other.map(_.toString).toSeq),
        updatedBy = c.updatedBy,
        license = c.copyright.flatMap(_.license),
        embedResources = embedResources,
        embedIds = embedIds
      )
    }

    def hitAsConceptSummary(hitString: String, language: String): api.ConceptSummary = {

      val searchableConcept = read[SearchableConcept](hitString)
      val titles = searchableConcept.title.languageValues.map(lv => domain.ConceptTitle(lv.value, lv.language))
      val contents = searchableConcept.content.languageValues.map(lv => domain.ConceptContent(lv.value, lv.language))
      val tags = searchableConcept.tags.languageValues.map(lv => domain.ConceptTags(lv.value, lv.language))

      val supportedLanguages = getSupportedLanguages(Seq(titles, contents))

      val title = Language
        .findByLanguageOrBestEffort(titles, language)
        .map(converterService.toApiConceptTitle)
        .getOrElse(api.ConceptTitle("", Language.UnknownLanguage))
      val concept = Language
        .findByLanguageOrBestEffort(contents, language)
        .map(converterService.toApiConceptContent)
        .getOrElse(api.ConceptContent("", Language.UnknownLanguage))
      val metaImage = Language
        .findByLanguageOrBestEffort(searchableConcept.metaImage, language)
        .map(converterService.toApiMetaImage)
        .getOrElse(api.ConceptMetaImage("", "", Language.UnknownLanguage))
      val tag = Language.findByLanguageOrBestEffort(tags, language).map(converterService.toApiTags)
      val subjectIds = Option(searchableConcept.subjectIds.toSet).filter(_.nonEmpty)

      api.ConceptSummary(
        id = searchableConcept.id,
        title = title,
        content = concept,
        metaImage = metaImage,
        tags = tag,
        subjectIds = subjectIds,
        supportedLanguages = supportedLanguages,
        lastUpdated = searchableConcept.lastUpdated.toDate,
        status = toApiStatus(searchableConcept.status),
        updatedBy = searchableConcept.updatedBy,
        license = searchableConcept.license
      )
    }

    def groupSubjectTagsByLanguage(subjectId: String, tags: List[api.ConceptTags]) =
      tags
        .groupBy(_.language)
        .map {
          case (lang, conceptTags) => {
            val tagsForLang = conceptTags.flatMap(_.tags).distinct
            api.SubjectTags(subjectId, tagsForLang, lang)
          }
        }
        .toList

    /**
      * Attempts to extract language that hit from highlights in elasticsearch response.
      *
      * @param result Elasticsearch hit.
      * @return Language if found.
      */
    def getLanguageFromHit(result: SearchHit): Option[String] = {
      def keyToLanguage(keys: Iterable[String]): Option[String] = {
        val keyLanguages = keys.toList.flatMap(key =>
          key.split('.').toList match {
            case _ :: language :: _ => Some(language)
            case _                  => None
        })

        keyLanguages
          .sortBy(lang => {
            ISO639.languagePriority.reverse.indexOf(lang)
          })
          .lastOption
      }

      val highlightKeys: Option[Map[String, _]] = Option(result.highlight)
      val matchLanguage = keyToLanguage(highlightKeys.getOrElse(Map()).keys)

      matchLanguage match {
        case Some(lang) =>
          Some(lang)
        case _ =>
          keyToLanguage(result.sourceAsMap.keys)
      }
    }

    def asApiConceptSearchResult(searchResult: SearchResult[api.ConceptSummary]): ConceptSearchResult =
      api.ConceptSearchResult(searchResult.totalCount,
                              searchResult.page,
                              searchResult.pageSize,
                              searchResult.language,
                              searchResult.results)

    def toApiStatus(status: Status): api.Status = {
      api.Status(
        current = status.current,
        other = status.other
      )
    }
  }
}
