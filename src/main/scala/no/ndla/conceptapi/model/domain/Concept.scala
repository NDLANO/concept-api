/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.model.domain

import java.util.Date

import no.ndla.conceptapi.ConceptApiProperties
import no.ndla.validation.{ValidationException, ValidationMessage}
import org.json4s.{DefaultFormats, FieldSerializer, Formats}
import org.json4s.FieldSerializer._
import org.json4s.ext.EnumNameSerializer
import org.json4s.native.Serialization._
import scalikejdbc._

import scala.util.{Failure, Success, Try}

case class Concept(
    id: Option[Long],
    revision: Option[Int],
    title: Seq[ConceptTitle],
    content: Seq[ConceptContent],
    copyright: Option[Copyright],
    source: Option[String],
    created: Date,
    updated: Date,
    metaImage: Seq[ConceptMetaImage],
    tags: Seq[ConceptTags],
    subjectIds: Set[String],
    articleId: Option[Long],
    status: Status
) {
  lazy val supportedLanguages: Set[String] =
    (content concat title).map(_.language).toSet
}

object Concept extends SQLSyntaxSupport[Concept] {
  override val tableName = "conceptdata"
  override val schemaName = Some(ConceptApiProperties.MetaSchema)

  // This Constructor is needed since json4s doesn't understand that it shouldn't attempt the other constructors if some fields are missing
  // Added cause metaImage are a new field and article-api doesn't dump it.
  // Can be removed after importing is done.
  def apply(
      id: Option[Long],
      revision: Option[Int],
      title: Seq[ConceptTitle],
      content: Seq[ConceptContent],
      copyright: Option[Copyright],
      source: Option[String],
      created: Date,
      updated: Date,
      articleId: Option[Long],
      status: Status
  ): Concept = {
    Concept(
      id,
      revision,
      title,
      content,
      copyright,
      source,
      created,
      updated,
      Seq.empty,
      Seq.empty,
      Set.empty,
      None,
      status
    )
  }

  def apply(lp: SyntaxProvider[Concept])(rs: WrappedResultSet): Concept =
    apply(lp.resultName)(rs)

  def apply(lp: ResultName[Concept])(rs: WrappedResultSet): Concept = {
    implicit val formats = this.JSonSerializer

    val id = rs.long(lp.c("id"))
    val revision = rs.int(lp.c("revision"))
    val jsonStr = rs.string(lp.c("document"))

    val meta = read[Concept](jsonStr)

    Concept(
      Some(id),
      Some(revision),
      meta.title,
      meta.content,
      meta.copyright,
      meta.source,
      meta.created,
      meta.updated,
      meta.metaImage,
      meta.tags,
      meta.subjectIds,
      meta.articleId,
      meta.status
    )
  }

  val JSonSerializer: Formats =
    DefaultFormats +
      FieldSerializer[Concept](
        ignore("id") orElse
          ignore("revision")
      ) +
      new EnumNameSerializer(ConceptStatus)
}

object PublishedConcept extends SQLSyntaxSupport[Concept] {
  override val tableName = "publishedconceptdata"
  override val schemaName = Some(ConceptApiProperties.MetaSchema)
}

object ConceptStatus extends Enumeration {

  val DRAFT, PUBLISHED, QUEUED_FOR_PUBLISHING, QUEUED_FOR_LANGUAGE, TRANSLATED, AWAITING_UNPUBLISHING, UNPUBLISHED,
  ARCHIVED = Value

  def valueOfOrError(s: String): Try[ConceptStatus.Value] =
    valueOf(s) match {
      case Some(st) => Success(st)
      case None =>
        val validStatuses = values.map(_.toString).mkString(", ")
        Failure(
          new ValidationException(
            errors =
              Seq(ValidationMessage("status", s"'$s' is not a valid article status. Must be one of $validStatuses"))))
    }

  def valueOf(s: String): Option[ConceptStatus.Value] = values.find(_.toString == s.toUpperCase)
}
