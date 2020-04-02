/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.repository

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.ConceptApiProperties
import no.ndla.conceptapi.integration.DataSource
import no.ndla.conceptapi.model.api.ConceptMissingIdException
import no.ndla.conceptapi.model.domain.{Concept, ConceptTags, PublishedConcept}
import org.json4s.Formats
import org.json4s.native.Serialization.{read, write}
import org.postgresql.util.PGobject
import scalikejdbc._

import scala.util.{Failure, Success, Try}

trait PublishedConceptRepository {
  this: DataSource =>
  val publishedConceptRepository: PublishedConceptRepository

  class PublishedConceptRepository extends LazyLogging with Repository[PublishedConcept] {
    implicit val formats: Formats = org.json4s.DefaultFormats + Concept.JSonSerializer

    def withId(id: Long): Option[PublishedConcept] = conceptWhere(sqls"co.id=${id.toInt}")

    def insert(concept: PublishedConcept) = {}

    private def conceptWhere(whereClause: SQLSyntax)(
        implicit session: DBSession = ReadOnlyAutoSession): Option[PublishedConcept] = {
      val co = Concept.syntax("co")
      sql"select ${co.result.*} from ${PublishedConcept.as(co)} where co.document is not NULL and $whereClause"
        .map(PublishedConcept(co))
        .single
        .apply()
    }

    override def documentsWithIdBetween(min: Long, max: Long): List[PublishedConcept] =
      conceptsWhere(sqls"co.id between $min and $max")

    private def conceptsWhere(whereClause: SQLSyntax)(
        implicit session: DBSession = ReadOnlyAutoSession): List[PublishedConcept] = {
      val co = Concept.syntax("co")
      sql"select ${co.result.*} from ${PublishedConcept.as(co)} where co.document is not NULL and $whereClause"
        .map(Concept(co))
        .list
        .apply()
    }

  }
}
