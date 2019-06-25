/*
 * Part of NDLA draft_api.
 * Copyright (C) 2017 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.model.domain

trait LanguageField {
  def isEmpty: Boolean
  def language: String
}
