/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.model.domain

import java.util.Date

import no.ndla.conceptapi.ConceptApiProperties
import org.json4s.FieldSerializer
import org.json4s.FieldSerializer._
import org.json4s.native.Serialization._
import scalikejdbc._

case class Concept(id: Option[Long],
                   title: Seq[ConceptTitle],
                   content: Seq[ConceptContent],
                   copyright: Option[Copyright],
                   created: Date,
                   updated: Date)
  {
  lazy val supportedLanguages: Set[String] = (content union title).map(_.language).toSet
}


object Concept extends SQLSyntaxSupport[Concept] {
  implicit val formats = org.json4s.DefaultFormats
  override val tableName = "conceptdata"
  override val schemaName = Some(ConceptApiProperties.MetaSchema)

  def apply(lp: SyntaxProvider[Concept])(rs: WrappedResultSet): Concept = apply(lp.resultName)(rs)

  def apply(lp: ResultName[Concept])(rs: WrappedResultSet): Concept = {
    val meta = read[Concept](rs.string(lp.c("document")))
    Concept(
      Some(rs.long(lp.c("id"))),
      meta.title,
      meta.content,
      meta.copyright,
      meta.created,
      meta.updated
    )
  }

  val JSonSerializer = FieldSerializer[Concept](
    ignore("id") orElse
      ignore("revision")
  )
}