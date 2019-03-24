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

package uk.gov.hmrc.gform.models

import cats.{ Monad, MonadError }
import cats.data.NonEmptyList
import cats.instances.future._
import cats.instances.int._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.eq._
import scala.concurrent.Future
import scala.util.Try
import uk.gov.hmrc.gform.controllers.AuthCacheWithForm
import uk.gov.hmrc.gform.graph.{ Data, Recalculation }
import uk.gov.hmrc.gform.keystore.RepeatingComponentService
import uk.gov.hmrc.gform.sharedmodel.{ HmrcTaxPeriodWithEvaluatedId, IdNumberValue, ObligationDetails, ObligationsResponse, TaxResponse }
import uk.gov.hmrc.gform.sharedmodel.form.{ FormDataRecalculated, FormField, ValidationResult, VisitIndex }
import uk.gov.hmrc.gform.sharedmodel.formtemplate.{ FormComponentId, HmrcTaxPeriod, Section }
import uk.gov.hmrc.http.HeaderCarrier

trait TaxPeriodConnect[F[_]] {
  def getTaxPeriod(request: NonEmptyList[HmrcTaxPeriodWithEvaluatedId]): F[NonEmptyList[TaxResponse]]
}

case class ProcessData(
  data: FormDataRecalculated,
  sections: List[Section],
  visitIndex: VisitIndex,
  obligationsResponse: ObligationsResponse
)

class ProcessDataService[F[_]: Monad, E](recalculation: Recalculation[F, E]) {

  def fetchTaxResponses(
    hmrcTaxPeriod: Map[(FormComponentId, HmrcTaxPeriod), IdNumberValue]
  )(
    implicit taxPeriodConnect: TaxPeriodConnect[F]
  ): F[Option[NonEmptyList[TaxResponse]]] =
    hmrcTaxPeriod.toList.map { case ((fcId, k), v) => HmrcTaxPeriodWithEvaluatedId(fcId, k, v) } match {
      case Nil     => Option.empty[NonEmptyList[TaxResponse]].pure[F]
      case x :: xs => taxPeriodConnect.getTaxPeriod(NonEmptyList(x, xs)).map(Some.apply)
    }

  def updateSectionVisits(dataRaw: Data, sections: List[Section], mongoSections: List[Section]): Set[Int] = {
    val visitIndex = dataRaw
      .get(FormComponentId(VisitIndex.key))
      .flatMap(_.headOption)
      .map(VisitIndex.fromString)
      .getOrElse(VisitIndex.empty)

    visitIndex.visitsIndex
      .map { index =>
        Try(mongoSections(index)).toOption.fold(-1) { section =>
          val firstComponentId = section.fields.head.id
          sections.indexWhere { s =>
            s.fields.head.id === firstComponentId
          }
        }
      }
      .filterNot(_ === -1)
  }

  def needRefresh(
    before: Map[(FormComponentId, HmrcTaxPeriod), IdNumberValue],
    after: Map[(FormComponentId, HmrcTaxPeriod), IdNumberValue]): Boolean =
    (after.isEmpty && !before.isEmpty) ||
      before
        .exists {
          case (hmrcTaxPeriod, idNumberValue) =>
            after.get(hmrcTaxPeriod) match {
              case None                 => true
              case Some(idNumberValue2) => idNumberValue != idNumberValue2
            }
        }

  def fetchTaxResponsesIfNeeded(
    before: Map[(FormComponentId, HmrcTaxPeriod), IdNumberValue],
    after: Map[(FormComponentId, HmrcTaxPeriod), IdNumberValue],
    cache: AuthCacheWithForm)(implicit taxPeriodConnect: TaxPeriodConnect[F]): F[ObligationsResponse] =
    if (needRefresh(before, after)) fetchTaxResponses(before).map(ObligationsResponse(_))
    else cache.form.thirdPartyData.obligationsResponse.pure[F]

  def clearTaxResponses(data: FormDataRecalculated): FormDataRecalculated =
    data.copy(recData = data.recData.copy(data = data.recData.data -- data.recData.hmrcTaxPeriod.keys.toList.map(_._1)))

  def getProcessData(dataRaw: Data, cache: AuthCacheWithForm)(
    implicit hc: HeaderCarrier,
    me: MonadError[F, E],
    taxPeriodConnect: TaxPeriodConnect[F]): F[ProcessData] =
    for {
      browserRecalculated <- recalculateDataAndSections(dataRaw, cache)
      mongoRecalculated   <- recalculateDataAndSections(cache.form.formData.toData, cache)
      (data, sections) = browserRecalculated
      (oldData, mongoSections) = mongoRecalculated
      responses <- fetchTaxResponsesIfNeeded(data.recData.hmrcTaxPeriod, oldData.recData.hmrcTaxPeriod, cache)
    } yield {

      val dataUpd =
        if (responses != cache.form.thirdPartyData.obligationsResponse)
          clearTaxResponses(data)
        else data

      val newVisitIndex = updateSectionVisits(dataRaw, sections, mongoSections)

      ProcessData(dataUpd, sections, VisitIndex(newVisitIndex), responses)
    }

  def recalculateDataAndSections(data: Data, cache: AuthCacheWithForm)(
    implicit hc: HeaderCarrier,
    me: MonadError[F, E]): F[(FormDataRecalculated, List[Section])] =
    for {
      formDataRecalculated <- recalculation
                               .recalculateFormData(
                                 data,
                                 cache.formTemplate,
                                 cache.retrievals,
                                 cache.form.thirdPartyData,
                                 cache.form.envelopeId)
    } yield {
      val sections = RepeatingComponentService.getAllSections(cache.formTemplate, formDataRecalculated)
      (formDataRecalculated, sections)
    }

}
