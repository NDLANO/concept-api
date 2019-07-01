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
case class UpdatedConcept(@(ApiModelProperty @field)(description =
                            "The language of this concept") language: String,
                          @(ApiModelProperty @field)(description =
                            "Available title for the concept") title: Option[
                            String],
                          @(ApiModelProperty @field)(description =
                            "The content of the concept") content: Option[
                            String],
                          @(ApiModelProperty @field)(description =
                            "Describes the copyright information for the concept") copyright: Option[
                            Copyright])
