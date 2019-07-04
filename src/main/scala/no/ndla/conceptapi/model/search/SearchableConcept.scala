/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.model.search

case class SearchableConcept(
    id: Long,
    title: SearchableLanguageValues,
    content: SearchableLanguageValues,
    defaultTitle: Option[String]
)
