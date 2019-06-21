/*
 * Part of NDLA concept_api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */


package no.ndla.conceptapi.controller

import org.scalatra.Ok

trait ConceptController
{

  class ConceptController extends NdlaController
  {
    get("/")
    {
      Ok("Hellow World")
    }
  }

}