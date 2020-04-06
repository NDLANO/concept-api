/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.repository

import com.typesafe.scalalogging.LazyLogging
import no.ndla.conceptapi.integration.DataSource
import no.ndla.conceptapi.model.domain.{Concept, PublishedConcept}
import org.json4s.Formats
import scalikejdbc._

trait PublishedConceptRepository {
  this: DataSource =>
  val publishedConceptRepository: PublishedConceptRepository

  class PublishedConceptRepository extends LazyLogging with Repository[Concept] {
    implicit val formats: Formats = Concept.JSonSerializer

    def withId(id: Long): Option[Concept] = conceptWhere(sqls"co.id=${id.toInt}")

    private def conceptWhere(whereClause: SQLSyntax)(
        implicit session: DBSession = ReadOnlyAutoSession): Option[Concept] = {
      val co = PublishedConcept.syntax("co")
      sql"select ${co.result.*} from ${PublishedConcept.as(co)} where co.document is not NULL and $whereClause"
        .map(Concept(co))
        .single
        .apply()
    }

    def conceptCount = ???

    override def documentsWithIdBetween(min: Long, max: Long): List[Concept] =
      conceptsWhere(sqls"co.id between $min and $max")

    override def minMaxId(implicit session: DBSession = AutoSession): (Long, Long) = {
      sql"select coalesce(MIN(id),0) as mi, coalesce(MAX(id),0) as ma from ${PublishedConcept.table}"
        .map(rs => {
          (rs.long("mi"), rs.long("ma"))
        })
        .single()
        .apply() match {
        case Some(minmax) => minmax
        case None         => (0L, 0L)
      }
    }

    private def conceptsWhere(whereClause: SQLSyntax)(
        implicit session: DBSession = ReadOnlyAutoSession): List[Concept] = {
      val co = PublishedConcept.syntax("co")
      sql"select ${co.result.*} from ${PublishedConcept.as(co)} where co.document is not NULL and $whereClause"
        .map(Concept(co))
        .list
        .apply()
    }

  }
}
