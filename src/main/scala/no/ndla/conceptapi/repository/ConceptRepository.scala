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
import no.ndla.conceptapi.model.api.{ConceptMissingIdException, NotFoundException}
import no.ndla.conceptapi.model.domain.Concept
import org.json4s.Formats
import org.postgresql.util.PGobject
import org.json4s.native.Serialization.write
import scalikejdbc._

import scala.util.{Failure, Success, Try}

trait ConceptRepository {
  this: DataSource =>
  val conceptRepository: ConceptRepository

  class ConceptRepository extends LazyLogging with Repository[Concept] {
    implicit val formats: Formats = org.json4s.DefaultFormats + Concept.JSonSerializer

    def insert(concept: Concept)(implicit session: DBSession = AutoSession): Concept = {
      val dataObject = new PGobject()
      dataObject.setType("jsonb")
      dataObject.setValue(write(concept))

      val conceptId: Long =
        sql"""
        insert into ${Concept.table} (document)
        values (${dataObject})
          """.updateAndReturnGeneratedKey.apply

      logger.info(s"Inserted new concept: $conceptId")
      concept.copy(id = Some(conceptId))
    }

    def insertwithListingId(concept: Concept, listingId: Long)(implicit session: DBSession = AutoSession): Concept = {
      val dataObject = new PGobject()
      dataObject.setType("jsonb")
      dataObject.setValue(write(concept))

      val conceptId: Long =
        sql"""
        insert into ${Concept.table} (listing_id, document)
        values ($listingId, $dataObject)
          """.updateAndReturnGeneratedKey.apply

      logger.info(s"Inserted new concept: '$conceptId', with listing id '$listingId'")
      concept.copy(id = Some(conceptId))
    }

    def updateWithListingId(concept: Concept, listingId: Long)(
        implicit session: DBSession = AutoSession): Try[Concept] = {
      val dataObject = new PGobject()
      dataObject.setType("jsonb")
      dataObject.setValue(write(concept))

      Try(sql"""
           update ${Concept.table} set document=${dataObject} where listing_id=${listingId}
         """.updateAndReturnGeneratedKey.apply) match {
        case Success(id) => Success(concept.copy(id = Some(id)))
        case Failure(ex) =>
          logger.warn(s"Failed to update concept with id ${concept.id} and listing id: $listingId: ${ex.getMessage}")
          Failure(ex)
      }
    }

    def withListingId(listingId: Long) =
      conceptWhere(sqls"co.listing_id=$listingId")

    def insertWithId(concept: Concept)(implicit session: DBSession = AutoSession): Try[Concept] = {
      concept.id match {
        case Some(id) =>
          val dataObject = new PGobject()
          dataObject.setType("jsonb")
          dataObject.setValue(write(concept))

          Try(sql"""insert into ${Concept.table} (id, document)
                    values ($id, ${dataObject})""".update.apply)

          logger.info(s"Inserted new concept: $id")
          Success(concept)
        case None =>
          Failure(ConceptMissingIdException("Attempted to insert concept without an id."))
      }
    }

    def update(concept: Concept)(implicit session: DBSession = AutoSession): Try[Concept] = {
      val dataObject = new PGobject()
      dataObject.setType("jsonb")
      dataObject.setValue(write(concept))

      Try(
        sql"update ${Concept.table} set document=${dataObject} where id=${concept.id.get}".updateAndReturnGeneratedKey.apply) match {
        case Success(id) => Success(concept.copy(id = Some(id)))
        case Failure(ex) =>
          logger.warn(s"Failed to update concept with id ${concept.id}: ${ex.getMessage}")
          Failure(ex)
      }
    }

    def withId(id: Long): Option[Concept] =
      conceptWhere(sqls"co.id=${id.toInt}")

    def exists(id: Long)(implicit session: DBSession = AutoSession): Boolean = {
      sql"select id from ${Concept.table} where id=${id}"
        .map(rs => rs.long("id"))
        .single
        .apply()
        .isDefined
    }

    def allSubjectIds(implicit session: DBSession = ReadOnlyAutoSession): Set[String] = {
      sql"""
        select distinct jsonb_array_elements_text(document->'subjectIds') as subject_id 
        from ${Concept.table} 
        where jsonb_array_length(document->'subjectIds') != 0;"""
        .map(rs => rs.string("subject_id"))
        .list
        .apply
        .toSet
    }

    def getIdFromExternalId(externalId: String)(implicit session: DBSession = AutoSession): Option[Long] = {
      sql"select id from ${Concept.table} where $externalId = any(external_id)"
        .map(rs => rs.long("id"))
        .single
        .apply()
    }

    override def minMaxId(implicit session: DBSession = AutoSession): (Long, Long) = {
      sql"select coalesce(MIN(id),0) as mi, coalesce(MAX(id),0) as ma from ${Concept.table}"
        .map(rs => {
          (rs.long("mi"), rs.long("ma"))
        })
        .single()
        .apply() match {
        case Some(minmax) => minmax
        case None         => (0L, 0L)
      }
    }

    override def documentsWithIdBetween(min: Long, max: Long): List[Concept] =
      conceptsWhere(sqls"co.id between $min and $max")

    private def conceptWhere(whereClause: SQLSyntax)(
        implicit session: DBSession = ReadOnlyAutoSession): Option[Concept] = {
      val co = Concept.syntax("co")
      sql"select ${co.result.*} from ${Concept.as(co)} where co.document is not NULL and $whereClause"
        .map(Concept(co))
        .single
        .apply()
    }

    private def conceptsWhere(whereClause: SQLSyntax)(
        implicit session: DBSession = ReadOnlyAutoSession): List[Concept] = {
      val co = Concept.syntax("co")
      sql"select ${co.result.*} from ${Concept.as(co)} where co.document is not NULL and $whereClause"
        .map(Concept(co))
        .list
        .apply()
    }

    def conceptCount(implicit session: DBSession = ReadOnlyAutoSession) =
      sql"select count(*) from ${Concept.table}"
        .map(rs => rs.long("count"))
        .single()
        .apply()
        .getOrElse(0)

    private def getHighestId(implicit session: DBSession = ReadOnlyAutoSession): Long = {
      sql"select id from ${Concept.table} order by id desc limit 1"
        .map(rs => rs.long("id"))
        .single()
        .apply()
        .getOrElse(0)
    }

    def updateIdCounterToHighestId()(implicit session: DBSession = AutoSession): Int = {
      val idToStartAt = SQLSyntax.createUnsafely((getHighestId() + 1).toString)
      val sequenceName = SQLSyntax.createUnsafely(
        s"${Concept.schemaName.getOrElse(ConceptApiProperties.MetaSchema)}.${Concept.tableName}_id_seq")

      sql"alter sequence $sequenceName restart with $idToStartAt;".executeUpdate().apply()
    }
  }
}
