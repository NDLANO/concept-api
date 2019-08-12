/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.model.api

import org.scalatra.swagger.annotations.{ApiModel, ApiModelProperty}

import scala.annotation.meta.field

@ApiModel(description = "Information about the concept")
case class NewConcept(
    @(ApiModelProperty @field)(description = "The language of this concept") language: String,
    @(ApiModelProperty @field)(description = "Available titles for the concept") title: String,
    @(ApiModelProperty @field)(description = "The content of the concept") content: Option[String],
    @(ApiModelProperty @field)(description = "Describes the copyright information for the concept") copyright: Option[
      Copyright],
    @(ApiModelProperty @field)(description = "An image-api ID for the concept meta image") metaImage: Option[
      NewConceptMetaImage],
    @(ApiModelProperty @field)(description = "A list of searchable tags") tags: Option[Seq[String]],
    @(ApiModelProperty @field)(description = "A list of taxonomy subject ids the concept is connected to") subjectIds: Option[
      Seq[String]]
)
