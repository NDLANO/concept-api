/*
 * Part of NDLA search_api.
 * Copyright (C) 2021 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.model.search

case class EmbedValues(
    id: Option[String],
    resource: Option[String],
    language: String
)
