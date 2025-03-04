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

package uk.gov.hmrc.gform.gform.handlers

import uk.gov.hmrc.gform.auth.models.MaterialisedRetrievals
import uk.gov.hmrc.gform.controllers.{ AuthCacheWithForm, Origin }
import uk.gov.hmrc.gform.fileupload.Envelope
import uk.gov.hmrc.gform.models.ExpandUtils.{ nonSubmittedFCsOfNonGroup, submittedFCs }
import uk.gov.hmrc.gform.models.ProcessData
import uk.gov.hmrc.gform.models.gform.{ FormComponentValidation, FormValidationOutcome }
import uk.gov.hmrc.gform.sharedmodel.form.{ EnvelopeId, FormDataRecalculated, ThirdPartyData, ValidationResult }
import uk.gov.hmrc.gform.sharedmodel.formtemplate._
import uk.gov.hmrc.gform.validation.FormFieldValidationResult
import uk.gov.hmrc.gform.validation.ValidationUtil.ValidatedType

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

class FormValidator(implicit ec: ExecutionContext) {

  def validateForm(
    data: FormDataRecalculated,
    sections: List[Section],
    sn: SectionNumber,
    cache: AuthCacheWithForm,
    envelope: Envelope,
    extractedValidateFormHelper: (
      List[FormComponentValidation],
      ValidatedType[ValidationResult]) => FormValidationOutcome,
    validateFormComponents: ValidateFormComponents[Future],
    evaluateValidation: EvaluateValidation
  )(
    implicit hc: HeaderCarrier
  ): Future[FormValidationOutcome] =
    validate(
      data,
      sections,
      sn,
      cache.form.envelopeId,
      envelope,
      cache.retrievals,
      cache.form.thirdPartyData,
      cache.formTemplate,
      validateFormComponents,
      evaluateValidation
    ).map {
      case (validationResult, validatedType, _) =>
        val fcvs: List[FormComponentValidation] = validationResult.map {
          case (formComponent, formFieldValidationResult) =>
            FormComponentValidation(formComponent, formFieldValidationResult)
        }
        extractedValidateFormHelper(fcvs, validatedType)
    }

  def validate(
    formDataRecalculated: FormDataRecalculated,
    sections: List[Section],
    sectionNumber: SectionNumber,
    envelopeId: EnvelopeId,
    envelope: Envelope,
    retrievals: MaterialisedRetrievals,
    thirdPartyData: ThirdPartyData,
    formTemplate: FormTemplate,
    validateFormComponents: ValidateFormComponents[Future],
    evaluateValidation: EvaluateValidation
  )(
    implicit hc: HeaderCarrier
  ): Future[(List[(FormComponent, FormFieldValidationResult)], ValidatedType[ValidationResult], Envelope)] = {
    val section = sections(sectionNumber.value)
    val nonSubmittedYet = nonSubmittedFCsOfNonGroup(formDataRecalculated, section)
    val allFC = submittedFCs(formDataRecalculated, sections.flatMap(_.expandSection(formDataRecalculated.data).allFCs)) ++ nonSubmittedYet
    val sectionFields = submittedFCs(formDataRecalculated, section.expandSectionRc(formDataRecalculated.data).allFCs) ++ nonSubmittedYet

    for {
      v <- validateFormComponents(
            sectionFields,
            section,
            envelopeId,
            retrievals,
            thirdPartyData,
            formTemplate,
            formDataRecalculated)
    } yield (evaluateValidation(v, allFC, formDataRecalculated, envelope), v, envelope)
  }

  def fastForwardValidate(
    processData: ProcessData,
    cache: AuthCacheWithForm,
    envelope: Envelope,
    extractedValidateFormHelper: (
      List[FormComponentValidation],
      ValidatedType[ValidationResult]) => FormValidationOutcome,
    validateFormComponents: ValidateFormComponents[Future],
    evaluateValidation: EvaluateValidation)(
    implicit hc: HeaderCarrier
  ): Future[Option[SectionNumber]] = {

    val sections = processData.sections
    val data = processData.data

    Origin(sections, data).availableSectionNumbers.foldLeft(Future.successful(None: Option[SectionNumber])) {
      case (accF, currentSn) =>
        accF.flatMap {
          case Some(sn) => Future.successful(Some(sn))
          case None =>
            validateForm(
              data,
              sections,
              currentSn,
              cache,
              envelope,
              extractedValidateFormHelper,
              validateFormComponents,
              evaluateValidation)
              .map {
                case FormValidationOutcome(isValid, _, _) =>
                  val section = sections(currentSn.value)
                  val hasBeenVisited = processData.visitIndex.visitsIndex.contains(currentSn.value)

                  val stop = section.continueIf.contains(Stop) || !hasBeenVisited
                  if (isValid && !stop) None else Some(currentSn)
              }
        }
    }
  }

}
