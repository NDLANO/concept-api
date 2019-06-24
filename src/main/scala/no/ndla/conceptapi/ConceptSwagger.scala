/*
 * Part of NDLA concept-api.
 * Copyright (C) 2019 NDLA
 *
 * See LICENSE
 */
/*
package no.ndla.draftapi

import org.scalatra.ScalatraServlet
import org.scalatra.swagger._

class ResourcesApp(implicit val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase {
  get("/") {
    renderSwagger2(swagger.docs.toList)
  }
}

object DraftApiInfo {

  val apiInfo = ApiInfo(
    "Concept API",
    "Services for accessing concepts",
    "http://ndla.no",
    ConceptApiProperties.ContactEmail,
    "GPL v3.0",
    "http://www.gnu.org/licenses/gpl-3.0.en.html"
  )
}

class ConceptSwagger extends Swagger("2.0", "1.0", ConceptApiInfo.apiInfo) {

  private def writeRolesInTest: List[String] = {
    val writeRoles = List(ConceptApiProperties.ConceptRoleWithWriteAccess)
                          DraftApiProperties.DraftRoleWithPublishAccess,
                          DraftApiProperties.ArticleRoleWithPublishAccess)
  }

  addAuthorization(
    OAuth(writeRolesInTest, List(ImplicitGrant(LoginEndpoint(ConceptApiProperties.Auth0LoginEndpoint), "access_token"))))
}
 */
