/*
 * Part of NDLA draft-api.
 * Copyright (C) 2018 NDLA
 *
 * See LICENSE
 */

package no.ndla.conceptapi.service

import cats.effect.IO
import no.ndla.conceptapi.auth.UserInfo
import no.ndla.conceptapi.model.api.IllegalStatusStateTransition
import no.ndla.conceptapi.model.domain.SideEffect.SideEffect
import no.ndla.conceptapi.model.domain
import no.ndla.conceptapi.model.domain.{ConceptStatus, SideEffect, StateTransition}
import no.ndla.conceptapi.repository.{ConceptRepository, PublishedConceptRepository}
import no.ndla.conceptapi.service.search.ConceptIndexService
import no.ndla.conceptapi.validation.ContentValidator
import no.ndla.conceptapi.model.domain.ConceptStatus._

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

trait StateTransitionRules {
  this: WriteService
    with ConceptRepository
    with PublishedConceptRepository
    with WriteService
    with ConverterService
    with ContentValidator
    with ConceptIndexService
    with PublishedConceptRepository =>

  object StateTransitionRules {

    private[service] val unpublishConcept: SideEffect = (concept: domain.Concept) => ??? // TODO:

    private[service] val publishConcept: SideEffect = (concept: domain.Concept) => ??? // TODO:

    import StateTransition._

    // format: off
    val StateTransitions: Set[StateTransition] = Set(
       DRAFT                      -> DRAFT,
       DRAFT                      -> ARCHIVED                    require UserInfo.WriteRoles illegalStatuses Set(PUBLISHED),
       ARCHIVED                   -> ARCHIVED,
       ARCHIVED                   -> DRAFT,
      (DRAFT                      -> PUBLISHED)                  keepStates Set() require UserInfo.WriteRoles withSideEffect publishConcept,
       QUEUED_FOR_PUBLISHING      -> QUEUED_FOR_PUBLISHING,
      (QUEUED_FOR_PUBLISHING      -> PUBLISHED)                  keepStates Set() require UserInfo.WriteRoles withSideEffect publishConcept,
       QUEUED_FOR_PUBLISHING      -> DRAFT,
      (PUBLISHED                  -> DRAFT)                      keepCurrentOnTransition,
      (PUBLISHED                  -> AWAITING_UNPUBLISHING)      require UserInfo.WriteRoles  keepCurrentOnTransition, // TODO: Check if used in article?
      (PUBLISHED                  -> UNPUBLISHED)                keepStates Set() require UserInfo.WriteRoles withSideEffect unpublishConcept, // TODO: Check if used in article?
      (AWAITING_UNPUBLISHING      -> AWAITING_UNPUBLISHING)      keepCurrentOnTransition,
       AWAITING_UNPUBLISHING      -> DRAFT,
      (AWAITING_UNPUBLISHING      -> PUBLISHED)                  keepStates Set() require UserInfo.WriteRoles withSideEffect publishConcept,
      (AWAITING_UNPUBLISHING      -> UNPUBLISHED)                keepStates Set() require UserInfo.WriteRoles withSideEffect unpublishConcept,
      (UNPUBLISHED                -> PUBLISHED)                  keepStates Set() require UserInfo.WriteRoles withSideEffect publishConcept,
       UNPUBLISHED                -> DRAFT,
       UNPUBLISHED                -> UNPUBLISHED,
       UNPUBLISHED                -> ARCHIVED                    require UserInfo.WriteRoles illegalStatuses Set(PUBLISHED),
       QUEUED_FOR_LANGUAGE        -> QUEUED_FOR_LANGUAGE,
       QUEUED_FOR_LANGUAGE        -> TRANSLATED,
       TRANSLATED                 -> TRANSLATED,
    )
    // format: on

    private def getTransition(
        from: ConceptStatus.Value,
        to: ConceptStatus.Value,
        user: UserInfo
    ): Option[StateTransition] =
      StateTransitions
        .find(transition => transition.from == from && transition.to == to)
        .filter(t => user.hasRoles(t.requiredRoles))

    private[service] def doTransitionWithoutSideEffect(
        current: domain.Concept,
        to: ConceptStatus.Value,
        user: UserInfo
    ): (Try[domain.Concept], SideEffect) = {
      getTransition(current.status.current, to, user) match {
        case Some(t) =>
          val currentToOther =
            if (t.addCurrentStateToOthersOnTransition) Set(current.status.current)
            else Set.empty

          val containsIllegalStatuses = current.status.other.intersect(t.illegalStatuses)
          if (containsIllegalStatuses.nonEmpty) {
            val illegalStateTransition = IllegalStatusStateTransition(
              s"Cannot go to $to when concept contains $containsIllegalStatuses")
            return (Failure(illegalStateTransition), SideEffect.fromOutput(Failure(illegalStateTransition)))
          }
          val other = current.status.other.intersect(t.otherStatesToKeepOnTransition) ++ currentToOther
          val newStatus = domain.Status(to, other)
          val convertedArticle = current.copy(status = newStatus)
          (Success(convertedArticle), t.sideEffect)
        case None =>
          val illegalStateTransition = IllegalStatusStateTransition(
            s"Cannot go to $to when concept is ${current.status.current}")
          (Failure(illegalStateTransition), SideEffect.fromOutput(Failure(illegalStateTransition)))
      }
    }

    def doTransition(
        current: domain.Concept,
        to: ConceptStatus.Value,
        user: UserInfo
    ): IO[Try[domain.Concept]] = {
      val (convertedArticle, sideEffect) = doTransitionWithoutSideEffect(current, to, user)
      IO { convertedArticle.flatMap(concept => sideEffect(concept)) }
    }
  }
}
