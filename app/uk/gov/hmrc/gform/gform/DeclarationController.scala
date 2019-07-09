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

import cats.data.Validated.{ Invalid, Valid }
import cats.instances.future._
import cats.instances.list._
import cats.syntax.traverse._
import cats.syntax.validated._
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, Request }
import uk.gov.hmrc.gform.auditing.{ AuditService, loggingHelpers }
import uk.gov.hmrc.gform.auth.AuthService
import uk.gov.hmrc.gform.config.FrontendAppConfig
import uk.gov.hmrc.gform.controllers.{ AuthCacheWithForm, AuthenticatedRequestActions }
import uk.gov.hmrc.gform.controllers.helpers.FormDataHelpers.{ formDataMap, get, processResponseDataFromBody }
import uk.gov.hmrc.gform.models.helpers.Fields
import uk.gov.hmrc.gform.graph.{ EmailParameterRecalculation, RecData, Recalculation }
import uk.gov.hmrc.gform.sharedmodel.{ AccessCode, LangADT }
import uk.gov.hmrc.gform.fileupload.Envelope
import uk.gov.hmrc.gform.gformbackend.GformConnector
import uk.gov.hmrc.gform.lookup.LookupRegistry
import uk.gov.hmrc.gform.sharedmodel.form._
import uk.gov.hmrc.gform.sharedmodel.formtemplate._
import uk.gov.hmrc.gform.summarypdf.PdfGeneratorService
import uk.gov.hmrc.gform.validation.ValidationUtil.{ GformError, ValidatedType }
import uk.gov.hmrc.gform.validation.{ FormFieldValidationResult, ValidationService }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.gform.sharedmodel.formtemplate.destinations.{ DestinationWithCustomerId, Destinations }
import uk.gov.hmrc.gform.summary.{ SubmissionDetails, SummaryRenderingService }

import scala.concurrent.Future

class DeclarationController(
  i18nSupport: I18nSupport,
  config: FrontendAppConfig,
  auth: AuthenticatedRequestActions,
  gformConnector: GformConnector,
  auditService: AuditService,
  summaryRenderingService: SummaryRenderingService,
  pdfService: PdfGeneratorService,
  renderer: SectionRenderingService,
  validationService: ValidationService,
  authService: AuthService,
  recalculation: Recalculation[Future, Throwable],
  lookupRegistry: LookupRegistry
) extends FrontendController {

  def showDeclaration(maybeAccessCode: Option[AccessCode], formTemplateId: FormTemplateId): Action[AnyContent] =
    auth.async(formTemplateId, maybeAccessCode) { implicit request => implicit l => cache =>
      Future.successful(cache.form.status match {
        case Validated => Ok(renderDeclarationSection(cache, maybeAccessCode))
        case _         => BadRequest
      })
    }

  private def renderDeclarationSection(cache: AuthCacheWithForm, maybeAccessCode: Option[AccessCode])(
    implicit request: Request[_],
    l: LangADT) = {
    import i18nSupport._

    renderer
      .renderDeclarationSection(
        maybeAccessCode,
        cache.form,
        cache.formTemplate,
        cache.retrievals,
        ValidationResult.empty.valid,
        FormDataRecalculated.empty,
        Nil)
  }

  def reviewAccepted(formTemplateId: FormTemplateId, maybeAccessCode: Option[AccessCode]): Action[AnyContent] =
    auth.async(formTemplateId, maybeAccessCode) { implicit request => implicit l => cache =>
      for {
        submission <- gformConnector.submissionStatus(FormId(cache.retrievals, formTemplateId, maybeAccessCode))
        _          <- submitToBackEnd(Accepting, cache, maybeAccessCode, Some(SubmissionDetails(submission, "")))
      } yield Ok
    }

  def submitDeclaration(formTemplateId: FormTemplateId, maybeAccessCode: Option[AccessCode]): Action[AnyContent] =
    auth.async(formTemplateId, maybeAccessCode) { implicit request => implicit l => cacheOrig =>
      processResponseDataFromBody(request) { dataRaw: Map[FormComponentId, Seq[String]] =>
        get(dataRaw, FormComponentId("save")) match {
          case "Continue" :: Nil => continueToSubmitDeclaration(cacheOrig, dataRaw, maybeAccessCode)
          case _                 => Future.successful(BadRequest("Cannot determine action"))
        }
      }
    }

  private def continueToSubmitDeclaration(
    cache: AuthCacheWithForm,
    dataRaw: Map[FormComponentId, Seq[String]],
    maybeAccessCode: Option[AccessCode])(implicit request: Request[_], l: LangADT) = {

    import i18nSupport._

    val declarationData = FormDataRecalculated(Set.empty, RecData.fromData(dataRaw))
    for {
      cacheWithHiddenSectionDataRemoved <- removeHiddenSectionData(cache)
      valRes                            <- validationService.validateComponentsWithCache(cacheWithHiddenSectionDataRemoved, declarationData)
      response                          <- processValidation(valRes, maybeAccessCode, cacheWithHiddenSectionDataRemoved, declarationData)
    } yield response
  }

  private def removeHiddenSectionData(cache: AuthCacheWithForm)(implicit hc: HeaderCarrier) =
    recalculateFormData(cache).map { data =>
      val visibleFields: Seq[FormField] = VisibleFieldCalculator(cache.formTemplate, cache.form.formData, data)
      val updatedForm = cache.form.copy(formData = cache.form.formData.copy(fields = visibleFields))
      cache.copy(form = updatedForm)
    }

  private def recalculateFormData(cache: AuthCacheWithForm)(implicit hc: HeaderCarrier) =
    recalculation.recalculateFormData(
      extractFormDataFields(cache),
      cache.formTemplate,
      cache.retrievals,
      cache.form.thirdPartyData,
      cache.form.seed)

  private def extractFormDataFields(cache: AuthCacheWithForm) =
    cache.form.formData.fields.map {
      case FormField(id, value) => id -> (value :: Nil)
    }.toMap

  private def processValidation(
    valType: ValidatedType[Unit],
    maybeAccessCode: Option[AccessCode],
    cache: AuthCacheWithForm,
    data: FormDataRecalculated
  )(implicit request: Request[_], l: LangADT) = valType match {
    case Valid(())                     => processValid(cache, data, maybeAccessCode)
    case validationResult @ Invalid(_) => processInvalid(maybeAccessCode, cache, data, validationResult)
  }

  private def processInvalid(
    maybeAccessCode: Option[AccessCode],
    cache: AuthCacheWithForm,
    data: FormDataRecalculated,
    validationResult: Invalid[GformError])(implicit request: Request[_], l: LangADT) =
    Future.successful(Ok(createHtmlForInvalidSubmission(maybeAccessCode, cache, data, validationResult)))

  private def processValid(cache: AuthCacheWithForm, data: FormDataRecalculated, maybeAccessCode: Option[AccessCode])(
    implicit request: Request[_],
    l: LangADT) = {
    val updatedCache = cache.copy(form = updateFormWithDeclaration(cache.form, cache.formTemplate, data))
    submitToBackEnd(Signed, updatedCache, maybeAccessCode, None)
      .map(showAcknowledgement(updatedCache, maybeAccessCode, _))
  }

  private def submitToBackEnd(
    formStatus: FormStatus,
    cache: AuthCacheWithForm,
    maybeAccessCode: Option[AccessCode],
    submissionDetails: Option[SubmissionDetails])(implicit request: Request[_], l: LangADT) =
    for {
      _          <- updateUserData(cache.form, formStatus)
      customerId <- evaluateCustomerId(cache)
      _          <- handleSubmission(maybeAccessCode, cache, customerId, submissionDetails)
    } yield customerId

  private def showAcknowledgement(
    cache: AuthCacheWithForm,
    maybeAccessCode: Option[AccessCode],
    customerId: CustomerId)(implicit request: Request[_]) = {
    if (customerId.isEmpty)
      Logger.warn(s"DMS submission with empty customerId ${loggingHelpers.cleanHeaderCarrierHeader(hc)}")

    val submissionEventId = auditSubmissionEvent(cache, customerId)

    Redirect(
      uk.gov.hmrc.gform.gform.routes.AcknowledgementController
        .showAcknowledgement(maybeAccessCode, cache.form.formTemplateId, submissionEventId))
  }

  private def auditSubmissionEvent(cache: AuthCacheWithForm, customerId: CustomerId)(implicit request: Request[_]) =
    auditService.sendSubmissionEvent(
      cache.form,
      cache.formTemplate.sections :+ cache.formTemplate.declarationSection,
      cache.retrievals,
      customerId)

  private def updateUserData(updatedForm: Form, status: FormStatus)(implicit hc: HeaderCarrier) =
    gformConnector
      .updateUserData(
        updatedForm._id,
        UserData(
          updatedForm.formData,
          status,
          updatedForm.visitsIndex,
          updatedForm.thirdPartyData
        )
      )

  private def evaluateCustomerId(cache: AuthCacheWithForm)(implicit hc: HeaderCarrier) =
    customerIdExpressions(cache.formTemplate.destinations)
      .traverse { cid =>
        authService.evaluateCustomerId(
          cid,
          cache.retrievals,
          cache.formTemplate,
          formDataMap(cache.form.formData),
          cache.form.envelopeId)
      }
      .map(_.filter(!_.isEmpty).headOption.getOrElse(CustomerId.empty))

  private def customerIdExpressions(destinations: Destinations) = destinations match {
    case d: Destinations.DmsSubmission => List(d.customerId)
    case ds: Destinations.DestinationList =>
      ds.destinations.collect { case d: DestinationWithCustomerId => d.customerId }
  }

  private def handleSubmission(
    maybeAccessCode: Option[AccessCode],
    cache: AuthCacheWithForm,
    customerId: CustomerId,
    submissionDetails: Option[SubmissionDetails])(implicit request: Request[_], l: LangADT) =
    for {
      htmlForPDF     <- summaryRenderingService.createHtmlForPdf(maybeAccessCode, cache, submissionDetails)
      emailParameter <- EmailParameterRecalculation(cache).recalculateEmailParameters(recalculation)
      _ <- GformSubmission
            .handleSubmission(
              config,
              gformConnector,
              cache.retrievals,
              cache.formTemplate,
              emailParameter,
              maybeAccessCode,
              customerId,
              htmlForPDF,
              StructuredFormDataBuilder(cache.form, cache.formTemplate, lookupRegistry)
            )
    } yield ()

  private def createHtmlForInvalidSubmission(
    maybeAccessCode: Option[AccessCode],
    cache: AuthCacheWithForm,
    data: FormDataRecalculated,
    validationResult: Invalid[GformError])(implicit request: Request[_], l: LangADT) = {
    import i18nSupport._

    renderer.renderDeclarationSection(
      maybeAccessCode,
      cache.form,
      cache.formTemplate,
      cache.retrievals,
      validationResult,
      data,
      getErrorMap(validationResult, data, cache.formTemplate))
  }

  private def updateFormWithDeclaration(form: Form, formTemplate: FormTemplate, data: FormDataRecalculated) = {
    val fieldNames = data.data.keySet.map(_.value)
    val allDeclarationFields = Fields.flattenGroups(formTemplate.declarationSection.fields)
    val submissibleFormFields = allDeclarationFields.flatMap { fieldValue =>
      fieldNames
        .filter(_.startsWith(fieldValue.id.value))
        .map(name => FormField(FormComponentId(name), data.data(FormComponentId(name)).head))
    }
    val updatedFields = form.formData.fields ++ submissibleFormFields

    form.copy(formData = form.formData.copy(fields = updatedFields))
  }

  private def getErrorMap(
    validationResult: ValidatedType[ValidationResult],
    data: FormDataRecalculated,
    formTemplate: FormTemplate): List[(FormComponent, FormFieldValidationResult)] = {
    val declarationFields = Fields.flattenGroups(formTemplate.declarationSection.fields)
    validationService.evaluateValidation(validationResult, declarationFields, data, Envelope(Nil))
  }
}
