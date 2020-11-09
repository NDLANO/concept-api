/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.validation

import no.ndla.conceptapi.model.domain._
import no.ndla.conceptapi.repository.DraftConceptRepository
import no.ndla.conceptapi.service.ConverterService
import no.ndla.mapping.ISO639.get6391CodeFor6392CodeMappings
import no.ndla.mapping.License.getLicense
import org.joda.time.format.ISODateTimeFormat
import no.ndla.validation._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

trait ContentValidator {
  this: DraftConceptRepository with ConverterService =>
  val contentValidator: ContentValidator

  class ContentValidator {
    private val NoHtmlValidator = new TextValidator(allowHtml = false)
    private val HtmlValidator = new TextValidator(allowHtml = true)

    def validateDate(fieldName: String, dateString: String): Seq[ValidationMessage] = {
      val parser = ISODateTimeFormat.dateOptionalTimeParser()
      Try(parser.parseDateTime(dateString)) match {
        case Success(_) => Seq.empty
        case Failure(_) =>
          Seq(ValidationMessage(fieldName, "Date field needs to be in ISO 8601"))
      }

    }

    def validateConcept(concept: Concept, allowUnknownLanguage: Boolean): Try[Concept] = {
      val validationErrors =
        concept.content.flatMap(c => validateConceptContent(c, allowUnknownLanguage)) ++
          concept.title.flatMap(t => validateTitle(t.title, t.language, allowUnknownLanguage)) ++
          concept.visualElement.flatMap(ve => validateVisualElement(ve, allowUnknownLanguage))

      if (validationErrors.isEmpty) {
        Success(concept)
      } else {
        Failure(new ValidationException(errors = validationErrors))
      }
    }

    private def validateVisualElement(content: VisualElement, allowUnknownLanguage: Boolean): Seq[ValidationMessage] = {
      HtmlValidator
        .validate("visualElement", content.visualElement, requiredToOptional = Map("image" -> Seq("data-caption")))
        .toList ++
        validateLanguage("language", content.language, allowUnknownLanguage)
    }

    private def validateConceptContent(content: ConceptContent,
                                       allowUnknownLanguage: Boolean): Seq[ValidationMessage] = {
      NoHtmlValidator.validate("content", content.content).toList ++
        validateLanguage("language", content.language, allowUnknownLanguage)
    }

    private def validateTitle(title: String,
                              language: String,
                              allowUnknownLanguage: Boolean): Seq[ValidationMessage] = {
      NoHtmlValidator.validate(s"title.$language", title).toList ++
        validateLanguage("language", language, allowUnknownLanguage) ++
        validateLength(s"title.$language", title, 256) ++
        validateMinimumLength(s"title.$language", title, 1)
    }

    private def validateCopyright(copyright: Copyright): Seq[ValidationMessage] = {
      val licenseMessage = copyright.license.map(validateLicense).toSeq.flatten
      val contributorsMessages = copyright.creators.flatMap(validateAuthor) ++ copyright.processors
        .flatMap(validateAuthor) ++ copyright.rightsholders.flatMap(validateAuthor)
      val originMessage =
        copyright.origin
          .map(origin => NoHtmlValidator.validate("copyright.origin", origin))
          .toSeq
          .flatten

      licenseMessage ++ contributorsMessages ++ originMessage
    }

    private def validateLicense(license: String): Seq[ValidationMessage] = {
      getLicense(license) match {
        case None =>
          Seq(ValidationMessage("license.license", s"$license is not a valid license"))
        case _ => Seq()
      }
    }

    private def validateAuthor(author: Author): Seq[ValidationMessage] = {
      NoHtmlValidator.validate("author.type", author.`type`).toList ++
        NoHtmlValidator.validate("author.name", author.name).toList
    }

    private def validateLanguage(fieldPath: String,
                                 languageCode: String,
                                 allowUnknownLanguage: Boolean): Option[ValidationMessage] = {
      languageCode.nonEmpty && languageCodeSupported6391(languageCode, allowUnknownLanguage) match {
        case true => None
        case false =>
          Some(ValidationMessage(fieldPath, s"Language '$languageCode' is not a supported value."))
      }
    }

    private def validateLength(fieldPath: String, content: String, maxLength: Int): Option[ValidationMessage] = {
      if (content.length > maxLength)
        Some(ValidationMessage(fieldPath, s"This field exceeds the maximum permitted length of $maxLength characters"))
      else
        None
    }

    private def validateMinimumLength(fieldPath: String, content: String, minLength: Int): Option[ValidationMessage] =
      if (content.trim.length < minLength)
        Some(
          ValidationMessage(fieldPath,
                            s"This field does not meet the minimum length requirement of $minLength characters"))
      else
        None

    private def languageCodeSupported6391(languageCode: String, allowUnknownLanguage: Boolean): Boolean = {
      val languageCodes = get6391CodeFor6392CodeMappings.values.toSeq ++ (if (allowUnknownLanguage)
                                                                            Seq("unknown")
                                                                          else
                                                                            Seq.empty)
      languageCodes.contains(languageCode)
    }

  }
}
