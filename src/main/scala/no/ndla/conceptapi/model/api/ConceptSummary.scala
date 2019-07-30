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
case class ConceptSummary(
    @(ApiModelProperty @field)(description = "The unique id of the concept") id: Long,
    @(ApiModelProperty @field)(description = "Available titles for the concept") title: ConceptTitle,
    @(ApiModelProperty @field)(description = "The content of the concept in available languages") content: ConceptContent,
    @(ApiModelProperty @field)(description = "The metaImage of the concept") metaImage: ConceptMetaImage,
    @(ApiModelProperty @field)(description = "All available languages of the current concept") supportedLanguages: Seq[
      String])
