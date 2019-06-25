/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */


package no.ndla.conceptapi

import no.ndla.conceptapi.controller.ConceptController

object ComponentRegistry extends ConceptController{

  lazy val conceptController = new ConceptController()
}
