/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

import javax.servlet.ServletContext
import no.ndla.conceptapi.ComponentRegistry.{conceptController, resourcesApp, healthController}
import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle {

  override def init(context: ServletContext) {
    context.mount(conceptController, "/concept-api/v1/concepts", "concept")
    context.mount(resourcesApp, "/concept-api/api-docs")
    context.mount(healthController, "/health")
  }
}
