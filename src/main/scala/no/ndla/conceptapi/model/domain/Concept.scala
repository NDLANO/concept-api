/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.model.domain

import java.util.Date

import no.ndla.conceptapi.ConceptApiProperties
import org.json4s.{DefaultFormats, FieldSerializer}
import org.json4s.FieldSerializer._
import org.json4s.native.Serialization._
import scalikejdbc._

case class Concept(id: Option[Long],
                   title: Seq[ConceptTitle],
                   content: Seq[ConceptContent],
                   copyright: Option[Copyright],
                   source: Option[String],
                   created: Date,
                   updated: Date,
                   metaImage: Seq[ConceptMetaImage],
                   tags: Seq[ConceptTags],
                   subjectIds: Set[String],
                   articleId: Option[Long]) {
  lazy val supportedLanguages: Set[String] =
    (content union title).map(_.language).toSet
}

object Concept extends SQLSyntaxSupport[Concept] {
  implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats
  override val tableName = "conceptdata"
  override val schemaName = Some(ConceptApiProperties.MetaSchema)

  // This Constructor is needed since json4s doesn't understand that it shouldn't attempt the other constructors if some fields are missing
  // Added cause metaImage are a new field and article-api doesn't dump it.
  // Can be removed after importing is done.
  def apply(id: Option[Long],
            title: Seq[ConceptTitle],
            content: Seq[ConceptContent],
            copyright: Option[Copyright],
            source: Option[String],
            created: Date,
            updated: Date,
            articleId: Option[Long]): Concept = {
    new Concept(id, title, content, copyright, source, created, updated, Seq.empty, Seq.empty, Set.empty, None)
  }

  def apply(lp: SyntaxProvider[Concept])(rs: WrappedResultSet): Concept =
    apply(lp.resultName)(rs)

  def apply(lp: ResultName[Concept])(rs: WrappedResultSet): Concept = {
    val meta = read[Concept](rs.string(lp.c("document")))
    Concept(
      Some(rs.long(lp.c("id"))),
      meta.title,
      meta.content,
      meta.copyright,
      meta.source,
      meta.created,
      meta.updated,
      meta.metaImage,
      meta.tags,
      meta.subjectIds,
      meta.articleId
    )
  }

  val JSonSerializer: FieldSerializer[Concept] = FieldSerializer[Concept](
    ignore("id")
  )
}
