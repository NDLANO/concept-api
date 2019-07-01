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
import no.ndla.conceptapi.repository.UnitSuite

abstract class IntegrationSuite extends UnitSuite {
  setEnv(PropertyKeys.MetaUserNameKey, "postgres")
  setEnvIfAbsent(PropertyKeys.MetaPasswordKey, "hemmelig")
  setEnv(PropertyKeys.MetaResourceKey, "postgres")
  setEnv(PropertyKeys.MetaServerKey, "127.0.0.1")
  setEnv(PropertyKeys.MetaPortKey, "5432")
  setEnv(PropertyKeys.MetaSchemaKey, "conceptapitest")

  val testDataSource: HikariDataSource = getHikariDataSource
}
