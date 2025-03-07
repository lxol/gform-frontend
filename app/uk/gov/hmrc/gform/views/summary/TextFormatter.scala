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

package uk.gov.hmrc.gform.views.summary

import uk.gov.hmrc.gform.commons.BigDecimalUtil.toBigDecimalSafe
import uk.gov.hmrc.gform.commons.{ NumberFormatUtil, NumberSetScale }
import uk.gov.hmrc.gform.sharedmodel.formtemplate._
import uk.gov.hmrc.gform.validation.FormFieldValidationResult
import uk.gov.hmrc.gform.commons.NumberFormatUtil._

object TextFormatter {

  def formatText(validationResult: Option[FormFieldValidationResult]): String = {
    val currentValue = validationResult match {
      case Some(result) => result.getCurrentValue.getOrElse("")
      case None         => ""
    }

    def componentText(text: Text) =
      if (currentValue.isEmpty) {
        currentValue
      } else {
        text.constraint match {
          case PositiveNumber(_, _, _, Some(unit)) => currentValue + " " + unit
          case Number(_, _, _, Some(unit))         => currentValue + " " + unit
          case PositiveNumber(_, _, rm, None) =>
            getNumberConstraint(bd => NumberFormatUtil.roundAndFormat(bd, bd.scale, rm))
          case Number(_, _, rm, None) =>
            getNumberConstraint(bd => NumberFormatUtil.roundAndFormat(bd, bd.scale, rm))
          case s: Sterling =>
            getNumberConstraint(bd => currencyFormat.format(NumberSetScale.setScale(bd, 2, s.roundingMode)))
          case _ =>
            currentValue
        }
      }
    def getNumberConstraint(f: BigDecimal => String): String = {
      val poundOrComma = "[£,]".r
      val valueWithoutPoundsOrCommas: String = poundOrComma.replaceAllIn(currentValue, "")
      val maybeBigDecimal = toBigDecimalSafe(valueWithoutPoundsOrCommas)
      maybeBigDecimal.fold(currentValue)(f)
    }

    def getValue(componentType: ComponentType): String = componentType match {
      case x: Text => componentText(x)
      case _       => currentValue
    }

    validationResult
      .map(result => getValue(result.fieldValue.`type`))
      .getOrElse("")
  }

  def appendUnit(constraint: TextConstraint): String = constraint match {
    case PositiveNumber(_, _, _, Some(unit)) => unit
    case Number(_, _, _, Some(unit))         => unit
    case _                                   => ""
  }

  def isNumber(formComponent: FormComponent) = formComponent.`type` match {
    case Text(Number(_, _, _, _), _, _, _) | Text(PositiveNumber(_, _, _, _), _, _, _) => true
    case _                                                                             => false
  }
}
