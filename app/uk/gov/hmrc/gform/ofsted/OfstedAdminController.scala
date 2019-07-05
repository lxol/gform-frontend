/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.gform.ofsted

import play.Logger
import play.api.mvc.{ Action, AnyContent }
import uk.gov.hmrc.gform.config.AppConfig
import uk.gov.hmrc.gform.controllers.AuthenticatedRequestActions
import uk.gov.hmrc.gform.gformbackend.GformConnector
import uk.gov.hmrc.gform.sharedmodel.formtemplate.FormTemplateId
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class OfstedAdminController(appConfig: AppConfig, gformConnector: GformConnector, auth: AuthenticatedRequestActions)
    extends FrontendController {
  def adminReview(formTemplateId: FormTemplateId, assumedIdentity: String, redirectUri: String): Action[AnyContent] =
    auth.asyncAlbAuth(formTemplateId, assumedIdentity) { implicit request => implicit l => cache =>
      Logger.info(s"Admin is authorized: redirecting admin to following URI [$redirectUri]...")
      Future.successful(Redirect(redirectUri).withSession("assumed-identity" -> assumedIdentity))
    }
}
