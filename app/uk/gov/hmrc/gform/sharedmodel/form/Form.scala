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

package uk.gov.hmrc.gform.sharedmodel.form

import cats.Eq
import cats.instances.string._
import cats.syntax.eq._
import julienrf.json.derived
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.gform.sharedmodel._
import uk.gov.hmrc.gform.sharedmodel.formtemplate.{ UserId => _, _ }

import scala.util.Try

case class VisitIndex(visitsIndex: Set[Int]) extends AnyVal {
  def toFormField: FormField =
    FormField(FormComponentId(VisitIndex.key), visitsIndex.mkString(","))

  def toVisitsTuple: (FormComponentId, Seq[String]) =
    (FormComponentId(VisitIndex.key), visitsIndex.mkString(",") :: Nil)

  def visit(sectionNumber: SectionNumber): VisitIndex = VisitIndex(visitsIndex + sectionNumber.value)
}

object VisitIndex {

  val key = "_visits_"

  val empty = VisitIndex(Set.empty)

  implicit val format: OFormat[VisitIndex] = Json.format

  def fromString(s: String): VisitIndex =
    if (s.isEmpty) {
      VisitIndex.empty
    } else
      VisitIndex(
        s.split(",")
          .toList
          .flatMap { indexAsString =>
            Try(indexAsString.toInt).toOption.fold(List.empty[Int])(_ :: Nil)
          }
          .toSet)
}

case class Form(
  _id: FormId,
  seed: Seed,
  envelopeId: EnvelopeId,
  userId: UserId,
  formTemplateId: FormTemplateId,
  formData: FormData,
  status: FormStatus,
  visitsIndex: VisitIndex,
  thirdPartyData: ThirdPartyData,
  envelopeExpiryDate: Option[EnvelopeExpiryDate]
)

object Form {

  private val readEnvelopeId: Reads[EnvelopeId] =
    (__ \ "envelopeId").readNullable[EnvelopeId].map(_.getOrElse(EnvelopeId("")))

  private val seedWithFallbackToEnvelopeId: Reads[Seed] =
    (__ \ "seed").read[Seed].orElse {
      (__ \ "envelopeId").readNullable[EnvelopeId].map {
        case Some(envelopeId) => Seed(envelopeId.value)
        case None             => Seed("")
      }
    }

  private val thirdPartyDataWithFallback: Reads[ThirdPartyData] =
    (__ \ "thirdPartyData").read[ThirdPartyData]

  private val reads: Reads[Form] = (
    (FormId.format: Reads[FormId]) and
      seedWithFallbackToEnvelopeId and
      EnvelopeId.format and
      UserId.oformat and
      FormTemplateId.vformat and
      FormData.format and
      FormStatus.format and
      VisitIndex.format and
      thirdPartyDataWithFallback and
      EnvelopeExpiryDate.optionFormat
  )(Form.apply _)

  private val writes: OWrites[Form] = OWrites[Form](
    form =>
      FormId.format.writes(form._id) ++
        Seed.format.writes(form.seed) ++
        EnvelopeId.format.writes(form.envelopeId) ++
        UserId.oformat.writes(form.userId) ++
        FormTemplateId.oformat.writes(form.formTemplateId) ++
        FormData.format.writes(form.formData) ++
        FormStatus.format.writes(form.status) ++
        VisitIndex.format.writes(form.visitsIndex) ++
        Json.obj("thirdPartyData" -> ThirdPartyData.format.writes(form.thirdPartyData)) ++
        EnvelopeExpiryDate.optionFormat.writes(form.envelopeExpiryDate)
  )

  implicit val format: OFormat[Form] = OFormat[Form](reads, writes)

}

sealed trait FormStatus
case object InProgress extends FormStatus
case object Summary extends FormStatus
case object Validated extends FormStatus
case object Signed extends FormStatus
case object NeedsReview extends FormStatus
case object Accepting extends FormStatus
case object Rejecting extends FormStatus
case object Accepted extends FormStatus
case object Submitted extends FormStatus

object FormStatus {
  implicit val equal: Eq[FormStatus] = Eq.fromUniversalEquals

  implicit val format: OFormat[FormStatus] = derived.oformat

  val all: Set[FormStatus] = Set(InProgress, Summary, Validated, Signed, NeedsReview, Accepting, Accepted, Submitted)

  def unapply(s: String): Option[FormStatus] = all.find(_.toString === s)
}
