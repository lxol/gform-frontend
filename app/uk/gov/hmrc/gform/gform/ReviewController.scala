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

package uk.gov.hmrc.gform.gform

import play.api.Logger
import play.api.mvc.{ Action, AnyContent, Request, Result }
import uk.gov.hmrc.gform.auth.models.OperationWithForm
import uk.gov.hmrc.gform.auth.models.OperationWithForm.ForceUpdateFormStatus
import uk.gov.hmrc.gform.controllers.helpers.FormDataHelpers
import uk.gov.hmrc.gform.controllers.AuthenticatedRequestActionsAlgebra
import uk.gov.hmrc.gform.sharedmodel.form._
import uk.gov.hmrc.gform.sharedmodel.formtemplate._
import uk.gov.hmrc.gform.sharedmodel.AccessCode
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.gform.gformbackend.GformBackEndAlgebra

import scala.concurrent.{ ExecutionContext, Future }

class ReviewController(
  auth: AuthenticatedRequestActionsAlgebra[Future],
  gformBackEnd: GformBackEndAlgebra[Future],
  reviewService: ReviewService[Future])
    extends FrontendController {

  //TODO make all three a single endpoint
  def reviewAccepted(formTemplateId: FormTemplateId, maybeAccessCode: Option[AccessCode]): Action[AnyContent] =
    auth.authAndRetrieveForm(formTemplateId, maybeAccessCode, OperationWithForm.ReviewAccepted) {
      implicit request => implicit l => cache =>
        asyncToResult(reviewService.acceptForm(cache, maybeAccessCode, extractReviewData(request)))
    }

  def reviewReturned(formTemplateId: FormTemplateId, maybeAccessCode: Option[AccessCode]): Action[AnyContent] =
    auth.authAndRetrieveForm(formTemplateId, maybeAccessCode, OperationWithForm.ReviewReturned) {
      implicit request => implicit l => cache =>
        asyncToResult(reviewService.returnForm(cache, maybeAccessCode, extractReviewData(request)))
    }

  def reviewSubmitted(formTemplateId: FormTemplateId, maybeAccessCode: Option[AccessCode]): Action[AnyContent] =
    auth.authAndRetrieveForm(formTemplateId, maybeAccessCode, OperationWithForm.ReviewSubmitted) {
      implicit request => implicit l => cache =>
        asyncToResult(reviewService.submitFormBundle(cache, extractReviewData(request), maybeAccessCode))
    }

  def updateFormField(formTemplateId: FormTemplateId, maybeAccessCode: Option[AccessCode]): Action[AnyContent] =
    auth.authAndRetrieveForm(formTemplateId, maybeAccessCode, OperationWithForm.UpdateFormField) {
      implicit request => implicit l => cache =>
        val maybeUpdatedForm: Option[Form] = for {
          body  <- request.body.asJson
          field <- body.asOpt[FormField]
        } yield FormDataHelpers.updateFormField(cache.form, field)

        maybeUpdatedForm map { updated =>
          asyncToResult(gformBackEnd.updateUserData(updated, maybeAccessCode))
        } getOrElse Future.successful(BadRequest)
    }

  def forceUpdateFormStatus(formTemplateId: FormTemplateId, maybeAccessCode: Option[AccessCode], status: FormStatus) =
    auth.authAndRetrieveForm(formTemplateId, maybeAccessCode, ForceUpdateFormStatus) {
      implicit request => implicit l => cache =>
        asyncToResult(reviewService.forceUpdateFormStatus(cache, status, extractReviewData(request), maybeAccessCode))
    }

  private def extractReviewData(request: Request[AnyContent]) =
    request.body.asFormUrlEncoded
      .getOrElse(Map.empty[String, Seq[String]])
      .mapValues(_.headOption)
      .collect { case (k, Some(v)) => (k, v) }

  private def asyncToResult[A](async: Future[A])(implicit ec: ExecutionContext): Future[Result] =
    async
      .map(_ => Ok)
      .recoverWith {
        case e: Exception =>
          Logger.warn("Caught exception", e)
          Future.successful(BadRequest(
            s"Caught an exception while attempting the operation. The exception message was:\n----\n${e.getMessage}\n----"))
      }
}
