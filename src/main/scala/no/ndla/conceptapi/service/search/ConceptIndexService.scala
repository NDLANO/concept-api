/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service.search

import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.indexes.IndexRequest
import com.sksamuel.elastic4s.mappings.MappingDefinition
import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.ConceptApiProperties
import no.ndla.conceptapi.model.domain.Concept
import no.ndla.conceptapi.repository.{ConceptRepository, Repository}
import org.json4s.native.Serialization.write
import no.ndla.conceptapi.model.search.SearchableLanguageFormats

trait ConceptIndexService {
  this: IndexService with ConceptRepository with SearchConverterService =>
  val conceptIndexService: ConceptIndexService

  class ConceptIndexService extends LazyLogging with IndexService[Concept] {
    implicit val formats = SearchableLanguageFormats.JSonFormats
    override val documentType: String = ConceptApiProperties.ConceptSearchDocument
    override val searchIndex: String = ConceptApiProperties.ConceptSearchIndex
    override val repository: Repository[Concept] = conceptRepository

    override def createIndexRequest(concept: Concept, indexName: String): IndexRequest = {
      val source = write(searchConverterService.asSearchableConcept(concept))
      indexInto(indexName / documentType).doc(source).id(concept.id.get.toString)
    }

    def getMapping: MappingDefinition = {
      mapping(documentType).fields(
        List(
          intField("id"),
          keywordField("defaultTitle"),
          nestedField("metaImage").fields(
            keywordField("imageId"),
            keywordField("altText"),
            keywordField("language")
          )
        ) ++
          generateLanguageSupportedFieldList("title", keepRaw = true) ++
          generateLanguageSupportedFieldList("content")
      )
    }

  }

}
