/*
 * Part of NDLA draft_api.
 * Copyright (C) 2017 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.repository

import no.ndla.conceptapi.model.domain.Concept
import scalikejdbc.{AutoSession, DBSession}

trait Repository[T <: Concept] {
  def minMaxId(implicit session: DBSession = AutoSession): (Long, Long)
  def documentsWithIdBetween(min: Long, max: Long): Seq[T]
}
