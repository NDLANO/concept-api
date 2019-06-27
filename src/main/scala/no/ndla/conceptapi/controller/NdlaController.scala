/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */


package no.ndla.conceptapi.controller

import java.nio.file.AccessDeniedException

import com.typesafe.scalalogging.LazyLogging
import javax.servlet.http.HttpServletRequest
import no.ndla.conceptapi.ComponentRegistry
import no.ndla.conceptapi.model.api.{Error, NotFoundException, OptimisticLockException, ResultWindowTooLargeException, ValidationError}
import no.ndla.network.model.HttpRequestException
import no.ndla.validation.{ValidationException, ValidationMessage}
import org.elasticsearch.index.IndexNotFoundException
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.Serialization.read
import org.postgresql.util.PSQLException
import org.scalatra._
import org.scalatra.json.NativeJsonSupport

import scala.util.{Failure, Success, Try}

abstract class NdlaController() extends ScalatraServlet with NativeJsonSupport with LazyLogging {
  protected implicit val jsonFormats: Formats = DefaultFormats

  case class Param[T](paramName: String, description: String)(implicit mf: Manifest[T])

  error {
    case a: AccessDeniedException          => Forbidden(body = Error(Error.ACCESS_DENIED, a.getMessage))
    case v: ValidationException            => BadRequest(body = ValidationError(messages = v.errors))
    case n: NotFoundException              => NotFound(body = Error(Error.NOT_FOUND, n.getMessage))
    case o: OptimisticLockException        => Conflict(body = Error(Error.RESOURCE_OUTDATED, o.getMessage))
    case _: PSQLException =>
      ComponentRegistry.connectToDatabase()
      InternalServerError(Error(Error.DATABASE_UNAVAILABLE, Error.DATABASE_UNAVAILABLE_DESCRIPTION))
    case h: HttpRequestException =>
      h.httpResponse match {
        case Some(resp) if resp.is4xx => BadRequest(body = resp.body)
        case _ =>
          logger.error(s"Problem with remote service: ${h.getMessage}")
          BadGateway(body = Error.GenericError)
      }
    case t: Throwable =>
      logger.error(Error.GenericError.toString, t)
      InternalServerError(body = Error.GenericError)
  }

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
  def paramOrDefault(paramName: String, default: String)(implicit request: HttpServletRequest): String = {
    paramOrNone(paramName).getOrElse(default)
  }

}

