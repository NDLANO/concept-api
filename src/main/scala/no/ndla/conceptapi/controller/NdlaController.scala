/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */


package no.ndla.conceptapi.controller

import no.ndla.validation.{ValidationException, ValidationMessage}
import org.json4s.native.Serialization.read
import org.scalatra._

import scala.util.{Failure, Success, Try}

abstract class NdlaController() extends ScalatraServlet {

  def extract[T](json: String)(implicit mf: scala.reflect.Manifest[T]): Try[T] = {
    Try { read[T](json) } match {
      case Failure(e)    => Failure(new ValidationException(errors = Seq(ValidationMessage("body", e.getMessage))))
      case Success(data) => Success(data)
    }
  }

  def doOrAccessDenied(hasAccess: Boolean)(w: => Any): Any = {
    if (hasAccess) {
      w
    } else {
      errorHandler(new AccessDeniedException("Missing user/client-id or role"))
    }
  }

}

