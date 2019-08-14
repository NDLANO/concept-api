/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.model.api

import java.util.Date

import org.scalatra.swagger.annotations.{ApiModel, ApiModelProperty}

import scala.annotation.meta.field

@ApiModel(description = "Information about the concept")
case class Concept(
    @(ApiModelProperty @field)(description = "The unique id of the concept") id: Long,
    @(ApiModelProperty @field)(description = "Available titles for the concept") title: Option[ConceptTitle],
    @(ApiModelProperty @field)(description = "The content of the concept") content: Option[ConceptContent],
    @(ApiModelProperty @field)(description = "Describes the copyright information for the concept") copyright: Option[
      Copyright],
    @(ApiModelProperty @field)(description = "A meta image for the concept") metaImage: Option[ConceptMetaImage],
    @(ApiModelProperty @field)(description = "Search tags the concept is tagged with") tags: Option[ConceptTags],
    @(ApiModelProperty @field)(description = "Taxonomy subject ids the concept is connected to") subjectIds: Option[
      Set[String]],
    @(ApiModelProperty @field)(description = "When the concept was created") created: Date,
    @(ApiModelProperty @field)(description = "When the concept was last updated") updated: Date,
    @(ApiModelProperty @field)(description = "All available languages of the current concept") supportedLanguages: Set[
      String])
