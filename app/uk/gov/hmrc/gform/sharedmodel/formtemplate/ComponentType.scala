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

package uk.gov.hmrc.gform.sharedmodel.formtemplate

import cats.Eq
import cats.data.NonEmptyList
import cats.syntax.foldable._
import julienrf.json.derived
import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.util.Try
import uk.gov.hmrc.gform.graph.Data
import uk.gov.hmrc.gform.sharedmodel.{ LocalisedString, ValueClassFormat, formtemplate }
import uk.gov.hmrc.gform.sharedmodel.formtemplate.DisplayWidth.DisplayWidth
import uk.gov.hmrc.gform.sharedmodel.structuredform.{ FieldName, RoboticsXml, StructuredFormDataFieldNamePurpose }

sealed trait MultiField {

  def fields(formComponentId: FormComponentId): NonEmptyList[FormComponentId]

  def alternateNamesFor(fcId: FormComponentId): Map[StructuredFormDataFieldNamePurpose, FieldName] = Map.empty

}

sealed trait ComponentType

case class Text(
  constraint: TextConstraint,
  value: Expr,
  displayWidth: DisplayWidth = DisplayWidth.DEFAULT,
  toUpperCase: UpperCaseBoolean = IsNotUpperCase
) extends ComponentType

case class TextArea(
  constraint: TextConstraint,
  value: Expr,
  displayWidth: DisplayWidth = DisplayWidth.DEFAULT
) extends ComponentType

case class UkSortCode(value: Expr) extends ComponentType with MultiField {
  override def fields(id: FormComponentId): NonEmptyList[FormComponentId] = UkSortCode.fields(id)
}

object UkSortCode {
  val fields: FormComponentId => NonEmptyList[FormComponentId] = (id: FormComponentId) =>
    NonEmptyList.of("1", "2", "3").map(id.withSuffix)
}

case class Date(
  constraintType: DateConstraintType,
  offset: Offset,
  value: Option[DateValue]
) extends ComponentType with MultiField {
  override def fields(id: FormComponentId): NonEmptyList[FormComponentId] = Date.fields(id)
}

case object Date {
  val fields: FormComponentId => NonEmptyList[FormComponentId] = (id: FormComponentId) =>
    NonEmptyList.of("day", "month", "year").map(id.withSuffix)
}

case class Address(international: Boolean) extends ComponentType with MultiField {
  override def fields(id: FormComponentId): NonEmptyList[FormComponentId] = Address.fields(id)

  override def alternateNamesFor(fcId: FormComponentId): Map[StructuredFormDataFieldNamePurpose, FieldName] =
    Map(RoboticsXml -> FieldName(fcId.value.replace("street", "line")))

}

case object Address {
  val mandatoryFields: FormComponentId => List[FormComponentId] = id => List("street1").map(id.withSuffix)
  val optionalFields: FormComponentId => List[FormComponentId] = id =>
    List("street2", "street3", "street4", "uk", "postcode", "country").map(id.withSuffix)
  val fields: FormComponentId => NonEmptyList[FormComponentId] = id =>
    NonEmptyList.fromListUnsafe(mandatoryFields(id) ++ optionalFields(id))

}

object DisplayWidth extends Enumeration {
  type DisplayWidth = Value
  val XS, S, M, L, XL, XXL, DEFAULT = Value

  implicit val displayWidthReads: Reads[DisplayWidth] = Reads.enumNameReads(DisplayWidth)
  implicit val displayWidthWrites: Writes[DisplayWidth] = Writes.enumNameWrites
}

sealed trait UpperCaseBoolean

case object IsUpperCase extends UpperCaseBoolean
case object IsNotUpperCase extends UpperCaseBoolean

object UpperCaseBoolean {
  implicit val format: OFormat[UpperCaseBoolean] = derived.oformat
}

case class Choice(
  `type`: ChoiceType,
  options: NonEmptyList[LocalisedString],
  orientation: Orientation,
  selections: List[Int],
  optionHelpText: Option[NonEmptyList[LocalisedString]]
) extends ComponentType

sealed trait ChoiceType
final case object Radio extends ChoiceType
final case object Checkbox extends ChoiceType
final case object YesNo extends ChoiceType
final case object Inline extends ChoiceType

object ChoiceType {
  implicit val format: OFormat[ChoiceType] = derived.oformat
  implicit val equal: Eq[ChoiceType] = Eq.fromUniversalEquals
}

case class RevealingChoiceElement(choice: LocalisedString, revealingFields: List[FormComponent], selected: Boolean)
object RevealingChoiceElement {
  implicit val format: OFormat[RevealingChoiceElement] = derived.oformat
}
case class RevealingChoice(options: NonEmptyList[RevealingChoiceElement], multiValue: Boolean) extends ComponentType
object RevealingChoice {
  implicit val format: OFormat[RevealingChoice] = {
    import JsonUtils._
    derived.oformat
  }

  val slice: FormComponentId => Data => RevealingChoice => List[FormComponent] = fcId =>
    data =>
      revealingChoice => {
        for {
          indexes        <- data.get(fcId).toList.flatten.headOption.toList
          index          <- indexes.split(",")
          i              <- Try(index.toLong).toOption.toList
          rc             <- revealingChoice.options.get(i).toList
          revealingField <- rc.revealingFields
        } yield revealingField
  }

}

case class IdType(value: String) extends AnyVal
case class RegimeType(value: String) extends AnyVal

object IdType {
  implicit val format: OFormat[IdType] = ValueClassFormat.oformat("idType", IdType.apply, _.value)
}
object RegimeType {
  implicit val format: OFormat[RegimeType] = ValueClassFormat.oformat("regimeType", RegimeType.apply, _.value)
}

case class HmrcTaxPeriod(idType: IdType, idNumber: Expr, regimeType: RegimeType) extends ComponentType

object HmrcTaxPeriod {
  implicit val catsEq: Eq[HmrcTaxPeriod] = Eq.fromUniversalEquals
  implicit val format: OFormat[HmrcTaxPeriod] = derived.oformat
}

sealed trait Orientation
case object Vertical extends Orientation
case object Horizontal extends Orientation
object Orientation {

  implicit val format: OFormat[Orientation] = derived.oformat
}

sealed trait InfoType
case object StandardInfo extends InfoType

case object LongInfo extends InfoType

case object ImportantInfo extends InfoType

case object BannerInfo extends InfoType

case object NoFormat extends InfoType

object InfoType {
  implicit val format: OFormat[InfoType] = derived.oformat
}

case class Group(
  fields: List[FormComponent],
  orientation: Orientation,
  repeatsMax: Option[Int] = None,
  repeatsMin: Option[Int] = None,
  repeatLabel: Option[LocalisedString] = None,
  repeatAddAnotherText: Option[LocalisedString] = None
) extends ComponentType {
  val baseGroupList = GroupList(fields)
}

case class InformationMessage(infoType: InfoType, infoText: LocalisedString) extends ComponentType

case class FileUpload() extends ComponentType

object ComponentType {

  implicit def readsNonEmptyList[T: Reads]: Reads[NonEmptyList[T]] = Reads[NonEmptyList[T]] { json =>
    Json.fromJson[List[T]](json).flatMap {
      case Nil     => JsError(ValidationError(s"Required at least one element. Got: $json"))
      case x :: xs => JsSuccess(NonEmptyList(x, xs))
    }
  }

  implicit def writesNonEmptyList[T: Writes]: Writes[NonEmptyList[T]] = Writes[NonEmptyList[T]] { v =>
    JsArray((v.head :: v.tail).map(Json.toJson(_)))
  }

  implicit val format: OFormat[ComponentType] = derived.oformat
}
