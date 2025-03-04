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

import java.time.LocalDateTime

import cats.instances.future._
import cats.syntax.applicative._
import play.api.{ Logger, data }
import play.api.i18n.I18nSupport
import play.api.mvc._
import uk.gov.hmrc.gform.auth.models.{ IsAgent, MaterialisedRetrievals, OperationWithForm, OperationWithoutForm }
import uk.gov.hmrc.gform.config.FrontendAppConfig
import uk.gov.hmrc.gform.controllers._
import uk.gov.hmrc.gform.fileupload.{ Envelope, FileUploadService }
import uk.gov.hmrc.gform.gformbackend.GformConnector
import uk.gov.hmrc.gform.models.{ AccessCodeLink, AccessCodePage }
import uk.gov.hmrc.gform.sharedmodel._
import uk.gov.hmrc.gform.sharedmodel.form.{ Form, FormId, FormIdData, Submitted }
import uk.gov.hmrc.gform.sharedmodel.formtemplate.{ UserId => _, _ }
import uk.gov.hmrc.gform.views.html.form._
import uk.gov.hmrc.gform.views.html.hardcoded.pages._
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException }
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

case class AccessCodeForm(accessCode: Option[String], accessOption: String)

class NewFormController(
  frontendAppConfig: FrontendAppConfig,
  i18nSupport: I18nSupport,
  auth: AuthenticatedRequestActions,
  fileUploadService: FileUploadService,
  gformConnector: GformConnector,
  fastForwardService: FastForwardService
) extends FrontendController {

  import i18nSupport._

  implicit val frontendConfig: FrontendAppConfig = frontendAppConfig

  private val noAccessCode = Option.empty[AccessCode]

  def dashboard(formTemplateId: FormTemplateId) =
    auth.authWithoutRetrievingForm(formTemplateId, OperationWithoutForm.ViewDashboard) {
      implicit request => implicit lang => cache =>
        (cache.formTemplate.draftRetrievalMethod, cache.retrievals) match {
          case (BySubmissionReference, _)                    => showAccesCodePage(cache, BySubmissionReference)
          case (drm @ FormAccessCodeForAgents(_), IsAgent()) => showAccesCodePage(cache, drm)
          case _                                             => Redirect(routes.NewFormController.newOrContinue(formTemplateId)).pure[Future]
        }
    }

  /**
    * This handles cases when draftRetrievalMethod submissionReference or formAccessCodeForAgents has been
    * added to the formTemplate after form went live without draftRetrievalMethod.
    *
    * It will try to load form without accessCode and only if such a form doesn't exists present user with Access Code page
    *
    */
  private def showAccesCodePage(cache: AuthCacheWithoutForm, draftRetrievalMethod: DraftRetrievalMethod)(
    implicit request: Request[AnyContent],
    l: LangADT) = {
    val userId = UserId(cache.retrievals)
    val formTemplateId = cache.formTemplate._id
    val formIdData = FormIdData.Plain(userId, formTemplateId)

    def showAccessCodePage =
      draftRetrievalMethod match {
        case BySubmissionReference => showAccessCodeList(cache, userId, formTemplateId)
        case _ =>
          Ok(access_code_start(cache.formTemplate, AccessCodePage.form(draftRetrievalMethod), frontendAppConfig))
            .pure[Future]
      }

    handleForm(formIdData)(showAccessCodePage) { form =>
      Redirect(routes.NewFormController.newOrContinue(formTemplateId)).pure[Future]
    }
  }

  private def showAccessCodeList(cache: AuthCacheWithoutForm, userId: UserId, formTemplateId: FormTemplateId)(
    implicit request: Request[AnyContent],
    l: LangADT) =
    for {
      formOverviews <- gformConnector.getAllForms(userId, formTemplateId)
    } yield
      Ok(
        access_code_list(
          cache.formTemplate,
          formOverviews,
          frontendAppConfig
        )
      )

  def showAccessCode(formTemplateId: FormTemplateId): Action[AnyContent] =
    auth.authWithoutRetrievingForm(formTemplateId, OperationWithoutForm.ShowAccessCode) {
      implicit request => implicit l => cache =>
        Future.successful {
          val accessCode = request.flash.get(AccessCodePage.key)
          accessCode match {
            case Some(code) =>
              Ok(start_new_form(cache.formTemplate, AccessCodePage(code), frontendAppConfig))
            case None => Redirect(routes.NewFormController.dashboard(formTemplateId))
          }
        }
    }

  private val choice: data.Form[String] = play.api.data.Form(
    play.api.data.Forms.single(
      "decision" -> play.api.data.Forms.nonEmptyText
    ))

  def decision(formTemplateId: FormTemplateId): Action[AnyContent] =
    auth.authAndRetrieveForm(formTemplateId, noAccessCode, OperationWithForm.EditForm) {
      implicit request => implicit l => cache =>
        choice.bindFromRequest
          .fold(
            _ =>
              BadRequest(
                continue_form_page(
                  cache.formTemplate,
                  choice.bindFromRequest().withError("decision", "error.required"),
                  frontendAppConfig)).pure[Future], {
              case "continue" => fastForwardService.redirectContinue(cache, noAccessCode)
              case "delete"   => fastForwardService.deleteForm(cache)
              case _          => Redirect(routes.NewFormController.newOrContinue(formTemplateId)).pure[Future]
            }
          )
    }

  def newSubmissionReference(formTemplateId: FormTemplateId) =
    auth.authWithoutRetrievingForm(formTemplateId, OperationWithoutForm.EditForm) {
      implicit request => implicit l => cache =>
        newForm(formTemplateId, cache)
    }

  def newOrContinue(formTemplateId: FormTemplateId) =
    auth.authWithoutRetrievingForm(formTemplateId, OperationWithoutForm.EditForm) {
      implicit request => implicit l => cache =>
        val formIdData = FormIdData.Plain(UserId(cache.retrievals), formTemplateId)
        handleForm(formIdData)(newForm(formTemplateId, cache)) { form =>
          cache.formTemplate.draftRetrievalMethod match {
            case OnePerUser(ContinueOrDeletePage.Skip) | FormAccessCodeForAgents(ContinueOrDeletePage.Skip) =>
              fastForwardService.redirectContinue(cache.toAuthCacheWithForm(form), noAccessCode)
            case _ => Ok(continue_form_page(cache.formTemplate, choice, frontendAppConfig)).pure[Future]
          }
        }
    }

  private def newForm(formTemplateId: FormTemplateId, cache: AuthCacheWithoutForm)(
    implicit hc: HeaderCarrier,
    l: LangADT) = {

    def notFound(formIdData: FormIdData) =
      Future.failed(new NotFoundException(s"Form with id ${formIdData.toFormId} not found."))

    for {
      formIdData <- startFreshForm(formTemplateId, cache.retrievals)
      res <- handleForm(formIdData)(notFound(formIdData)) { form =>
              fastForwardService.redirectContinue(cache.toAuthCacheWithForm(form), formIdData.maybeAccessCode)
            }
    } yield res
  }

  def newFormPost(formTemplateId: FormTemplateId): Action[AnyContent] = {
    def badRequest(formTemplate: FormTemplate, errors: play.api.data.Form[AccessCodeForm])(
      implicit request: Request[AnyContent],
      lang: LangADT) =
      BadRequest(access_code_start(formTemplate, errors, frontendAppConfig))

    def notFound(formTemplate: FormTemplate)(implicit request: Request[AnyContent], lang: LangADT) =
      badRequest(
        formTemplate,
        AccessCodePage
          .form(formTemplate.draftRetrievalMethod)
          .bindFromRequest()
          .withError(AccessCodePage.key, "error.notfound")
      )

    def noAccessCodeProvided = Future.failed[Result](new Exception(s"AccessCode not provided, cannot continue."))

    def optionAccess(
      accessCodeForm: AccessCodeForm,
      cache: AuthCacheWithoutForm)(implicit hc: HeaderCarrier, request: Request[AnyContent], lang: LangADT) = {
      val maybeAccessCode: Option[AccessCode] = accessCodeForm.accessCode.map(a => AccessCode(a))
      maybeAccessCode.fold(noAccessCodeProvided) { accessCode =>
        val formIdData = FormIdData.WithAccessCode(UserId(cache.retrievals), formTemplateId, accessCode)
        handleForm(formIdData)(notFound(cache.formTemplate).pure[Future]) { form =>
          fastForwardService.redirectContinue(cache.toAuthCacheWithForm(form), Some(accessCode))
        }
      }
    }

    def processNewFormData(formIdData: FormIdData, drm: DraftRetrievalMethod)(implicit request: Request[AnyContent]) =
      formIdData match {
        case FormIdData.WithAccessCode(_, formTemplateId, accessCode) =>
          Redirect(routes.NewFormController.showAccessCode(formTemplateId))
            .flashing(AccessCodePage.key -> accessCode.value)
            .pure[Future]
        case FormIdData.Plain(_, _) =>
          Future.failed(
            new Exception(
              s"newFormPost endpoind for DraftRetrievalMethod: $drm is being seen as OnePerUser on the backend"))
      }

    def processSubmittedData(cache: AuthCacheWithoutForm, drm: DraftRetrievalMethod)(
      implicit request: Request[AnyContent],
      l: LangADT): Future[Result] =
      AccessCodePage
        .form(drm)
        .bindFromRequest
        .fold(
          (hasErrors: data.Form[AccessCodeForm]) => Future.successful(badRequest(cache.formTemplate, hasErrors)),
          accessCodeForm => {
            accessCodeForm.accessOption match {
              case AccessCodePage.optionNew =>
                for {
                  formIdData <- startFreshForm(formTemplateId, cache.retrievals)
                  result     <- processNewFormData(formIdData, drm)
                } yield result
              case AccessCodePage.optionAccess =>
                optionAccess(accessCodeForm, cache)
            }
          }
        )

    auth.authWithoutRetrievingForm(formTemplateId, OperationWithoutForm.EditForm) {
      implicit request => implicit lang => cache =>
        (cache.formTemplate.draftRetrievalMethod, cache.retrievals) match {
          case (BySubmissionReference, _)                    => processSubmittedData(cache, BySubmissionReference)
          case (drm @ FormAccessCodeForAgents(_), IsAgent()) => processSubmittedData(cache, drm)
          case otherwise =>
            Future.failed(
              new Exception(
                s"newFormPost endpoind called, but draftRetrievalMethod is not allowed for a user or formTemplate: $otherwise")
            )
        }
    }
  }

  def continue(formTemplateId: FormTemplateId, submissionRef: SubmissionRef) =
    auth.authWithoutRetrievingForm(formTemplateId, OperationWithoutForm.EditForm) {
      implicit request => implicit lang => cache =>
        val userId = UserId(cache.retrievals)

        def notFound = {
          Logger.error(s"Form not found for formTemplateId $formTemplateId and submissionRef: $submissionRef")
          showAccessCodeList(cache, userId, formTemplateId)
        }

        val accessCode = AccessCode.fromSubmissionRef(submissionRef)
        val formIdData = FormIdData.WithAccessCode(userId, formTemplateId, accessCode)
        handleForm(formIdData)(notFound) { form =>
          fastForwardService.redirectContinue(cache.toAuthCacheWithForm(form), Some(accessCode))
        }
    }

  private def getForm(formIdData: FormIdData)(implicit hc: HeaderCarrier): Future[Option[Form]] =
    for {
      maybeForm <- gformConnector.maybeForm(formIdData)
      maybeFormExceptSubmitted = maybeForm.filter(_.status != Submitted)
      maybeEnvelope <- maybeFormExceptSubmitted.fold(Option.empty[Envelope].pure[Future]) { f =>
                        fileUploadService.getMaybeEnvelope(f.envelopeId)
                      }
      mayBeFormExceptWithEnvelope <- (maybeFormExceptSubmitted, maybeEnvelope) match {
                                      case (None, _)          => None.pure[Future]
                                      case (Some(f), None)    => gformConnector.deleteForm(f._id).map(_ => None)
                                      case (Some(_), Some(_)) => maybeFormExceptSubmitted.pure[Future]
                                    }
    } yield mayBeFormExceptWithEnvelope

  private def startFreshForm(formTemplateId: FormTemplateId, retrievals: MaterialisedRetrievals)(
    implicit hc: HeaderCarrier): Future[FormIdData] =
    for {
      newFormData <- gformConnector
                      .newForm(formTemplateId, UserId(retrievals), AffinityGroupUtil.fromRetrievals(retrievals))
    } yield newFormData

  private def handleForm[A](formIdData: FormIdData)(notFound: => Future[A])(found: Form => Future[A])(
    implicit hc: HeaderCarrier): Future[A] =
    for {
      maybeForm <- getForm(formIdData)
      result    <- maybeForm.fold(notFound)(found)
    } yield result
}
