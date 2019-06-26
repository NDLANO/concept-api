/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */


package no.ndla.conceptapi.controller

import java.nio.file.AccessDeniedException

import javax.servlet.http.HttpServletRequest
import no.ndla.validation.{ValidationException, ValidationMessage}
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.Serialization.read
import org.scalatra._
import org.scalatra.json.NativeJsonSupport

import scala.util.{Failure, Success, Try}

abstract class NdlaController() extends ScalatraServlet with NativeJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  case class Param[T](paramName: String, description: String)(implicit mf: Manifest[T])

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

  def long(paramName: String)(implicit request: HttpServletRequest): Long = {
    val paramValue = params(paramName)
    paramValue.forall(_.isDigit) match {
      case true => paramValue.toLong
      case false =>
        throw new ValidationException(
          errors = Seq(ValidationMessage(paramName, s"Invalid value for $paramName. Only digits are allowed.")))
    }
  }

  def paramOrNone(paramName: String)(implicit request: HttpServletRequest): Option[String] = {
    params.get(paramName).map(_.trim).filterNot(_.isEmpty())
  }

}

