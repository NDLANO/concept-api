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
import org.postgresql.util.PGobject
import scalikejdbc._
import org.json4s.native.Serialization.write

import scala.util.{Failure, Success, Try}

trait PublishedConceptRepository {
  this: DataSource =>
  val publishedConceptRepository: PublishedConceptRepository

  class PublishedConceptRepository extends LazyLogging with Repository[Concept] {
    implicit val formats: Formats = Concept.JSonSerializer

    def insertOrUpdate(concept: Concept)(implicit session: DBSession = AutoSession): Try[Concept] = {
      val dataObject = new PGobject()
      dataObject.setType("jsonb")
      dataObject.setValue(write(concept))

      Try {
        sql"""update ${PublishedConcept.table}
              set 
                document=$dataObject,
                revision=${concept.revision}
              where id=${concept.id}
          """.update.apply
      } match {
        case Success(count) if count == 1 =>
          logger.info(s"Updated published concept ${concept.id}")
          Success(concept)
        case Success(_) =>
          logger.info(s"No published concept with id ${concept.id} exists, creating...")
          Try {
            sql"""
                  insert into ${PublishedConcept.table} (id, document, revision)
                  values (${concept.id}, $dataObject, ${concept.revision})
              """.updateAndReturnGeneratedKey().apply
          }.map(_ => concept)
        case Failure(ex) => Failure(ex)
      }
    }

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