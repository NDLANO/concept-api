/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi

import com.zaxxer.hikari.HikariDataSource
import no.ndla.network.secrets.PropertyKeys
import no.ndla.conceptapi.integration.DataSource.getHikariDataSource

import scala.util.Try

abstract class IntegrationSuite extends UnitSuite {
  setEnvIfAbsent(PropertyKeys.MetaUserNameKey, "postgres")
  setEnvIfAbsent(PropertyKeys.MetaPasswordKey, "hemmelig")
  setEnvIfAbsent(PropertyKeys.MetaResourceKey, "postgres")
  setEnvIfAbsent(PropertyKeys.MetaServerKey, "127.0.0.1")
  setEnvIfAbsent(PropertyKeys.MetaPortKey, "5432")
  setEnvIfAbsent(PropertyKeys.MetaSchemaKey, "conceptapitest")

  val testDataSource: Try[HikariDataSource] = Try(getHikariDataSource)
}
