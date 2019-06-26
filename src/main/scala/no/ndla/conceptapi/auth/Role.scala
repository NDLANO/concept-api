/*
 * Part of NDLA draft_api.
 * Copyright (C) 2017 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.auth

object Role extends Enumeration {
  val WRITE = Value

  def valueOf(s: String): Option[Role.Value] = {
    val role = s.split("concepts:")
    Role.values.find(_.toString == role.lastOption.getOrElse("").toUpperCase)
  }
}
