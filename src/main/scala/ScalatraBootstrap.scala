/*
 * Part of NDLA concept_api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

import javax.servlet.ServletContext
import no.ndla.conceptapi.ComponentRegistry.{
  conceptController
}

import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle {

  override def init(context: ServletContext) {
    context.mount(conceptController, "/", "concept")
    }

}