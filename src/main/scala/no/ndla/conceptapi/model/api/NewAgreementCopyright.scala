/*
 * Part of NDLA draft_api.
 * Copyright (C) 2017 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.model.api

import org.scalatra.swagger.annotations.{ApiModel, ApiModelProperty}

import scala.annotation.meta.field

@ApiModel(description = "Description of agreement copyright information")
case class NewAgreementCopyright(
    @(ApiModelProperty @field)(description = "Describes the license of the article") license: Option[License],
    @(ApiModelProperty @field)(description = "Reference to where the article is procured") origin: Option[String],
    @(ApiModelProperty @field)(description = "List of creators") creators: Seq[Author],
    @(ApiModelProperty @field)(description = "List of processors") processors: Seq[Author],
    @(ApiModelProperty @field)(description = "List of rightsholders") rightsholders: Seq[Author],
    @(ApiModelProperty @field)(description = "Reference to agreement id") agreementId: Option[Long],
    @(ApiModelProperty @field)(description = "Date from which the copyright is valid") validFrom: Option[String],
    @(ApiModelProperty @field)(description = "Date to which the copyright is valid") validTo: Option[String])
