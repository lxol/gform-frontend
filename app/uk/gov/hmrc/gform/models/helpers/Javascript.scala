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

package uk.gov.hmrc.gform.models.helpers

import uk.gov.hmrc.gform.models.Dependecies
import uk.gov.hmrc.gform.sharedmodel.formtemplate._
import FormComponentHelper.extractMaxFractionalDigits
import uk.gov.hmrc.gform.ops._

case class JsFunction(name: String) extends AnyVal {
  override def toString = name
}

case class RepeatFormComponentIds(op: FormComponentId => List[FormComponentId]) extends AnyVal

object Javascript {

  def fieldJavascript(
    sectionFields: List[FormComponentWithCtx],
    allFields: List[FormComponent],
    repeatFormComponentIds: RepeatFormComponentIds,
    dependencies: Dependecies): String = {

    val sectionFieldIds = sectionFields.map(_.id).toSet

    def isDynamic(expr: Expr): Boolean = expr match {
      case f @ FormCtx(_)              => sectionFieldIds.contains(f.toFieldId)
      case Sum(f)                      => isDynamic(f)
      case Add(field1, field2)         => isDynamic(field1) || isDynamic(field2)
      case Subtraction(field1, field2) => isDynamic(field1) || isDynamic(field2)
      case Multiply(field1, field2)    => isDynamic(field1) || isDynamic(field2)
      case otherwise                   => false
    }

    val fieldIdWithExpr: List[(FormComponentWithCtx, Expr)] =
      sectionFields.collect {
        case formComponent @ HasExprCtx(SingleExpr(expr)) if isDynamic(expr) =>
          (formComponent, expr)
      }

    val groupFoldButtons: List[(FormComponentId, FormComponentWithGroup)] =
      sectionFields.collect {
        case wg @ FormComponentWithGroup(fc, parent)
            if parent.presentationHint.fold(false)(_.exists(_ == CollapseGroupUnderLabel)) =>
          fc.id -> wg
      }

    fieldIdWithExpr
      .map(x => toJavascriptFn(x._1, x._2, repeatFormComponentIds, dependencies.toLookup, groupFoldButtons.toMap))
      .mkString("\n") +
      """|function getValue(elementId, identity) {
         |   var el = document.getElementById(elementId);
         |   if (el) {
         |     return getNumber(el.value.replace(/[£,]/g,''), identity);
         |   } else {
         |     return identity;
         |   };
         |};
         |
         |function getNumber(value, identity) {
         |  if (value == ""){
         |    return identity;
         |  } else {
         |   return value.replace(",", "");
         |  }
         |};
         |
         |function add(a, b) {
         |  return BigNumber(a).plus(BigNumber(b));
         |};
         |
         |function subtract(a, b) {
         |  return BigNumber(a).minus(BigNumber(b));
         |};
         |
         |function multiply(a, b) {
         |  return BigNumber(a).times(BigNumber(b));
         |};
         |function displaySterling(result) {
         |
         |  return result < 0 ? result.replace("-", "-£") : '£' + BigNumber(result).toFormat(2)
         |};
         |""".stripMargin
  }

  private def toJavascriptFn(
    field: FormComponentWithCtx,
    expr: Expr,
    repeatFormComponentIds: RepeatFormComponentIds,
    dependenciesLookup: Map[FormComponentId, List[FormComponentId]],
    groupFoldButtonLookup: Map[FormComponentId, FormComponentWithGroup]): String = {

    def getRoundingMode(fc: FormComponent) =
      fc.`type` match {
        case Text(Number(_, _, rm, _), _, _, _)          => Some(rm)
        case TextArea(Number(_, _, rm, _), _, _)         => Some(rm)
        case Text(PositiveNumber(_, _, rm, _), _, _, _)  => Some(rm)
        case TextArea(PositiveNumber(_, _, rm, _), _, _) => Some(rm)
        case Text(s: Sterling, _, _, _)                  => Some(s.roundingMode)
        case TextArea(s: Sterling, _, _)                 => Some(s.roundingMode)
        case _                                           => None
      }

    val roundingMode = field match {
      case FormComponentWithGroup(fc, _) => getRoundingMode(fc)
      case FormComponentSimple(fc)       => getRoundingMode(fc)
    }

    import Expr._

    def computeExpr(expr: Expr, opIdentity: Int): String = {

      def sum(id: String) = {
        val groupFcIds: List[FormComponentId] = repeatFormComponentIds.op(FormComponentId(id))
        val sumExpr = groupFcIds.map(x => FormCtx(x.value)).foldLeft(additionIdentityExpr)(Add)
        computeExpr(sumExpr, additionIdentity)
      }

      def compute(operation: String, left: Expr, right: Expr, id: Int) =
        s"$operation(${computeExpr(left, id)}, ${computeExpr(right, id)})"

      expr match {
        case FormCtx(id)       => s"""getValue("$id", $opIdentity)"""
        case Constant(amount)  => amount
        case Add(a, b)         => compute("add", a, b, additionIdentity)
        case Subtraction(a, b) => compute("subtract", a, b, additionIdentity)
        case Multiply(a, b)    => compute("multiply", a, b, multiplicationIdentity)
        case Sum(FormCtx(id))  => sum(id)
        case otherwise         => ""
      }
    }

    def listeners(functionName: JsFunction) = {

      def hasFoldButton(id: FormComponentId) =
        groupFoldButtonLookup.get(id) match {
          case None => ""
          case Some(FormComponentWithGroup(_, parent)) =>
            val id = parent.id
            s"""|var element$id = document.getElementById("$id")
                |element$id.addEventListener("change",$functionName);
                |""".stripMargin
        }

      val windowEl = s"""window.addEventListener("load", $functionName);"""

      val componentEls =
        dependenciesLookup.get(field.id) match {
          case None => ""
          case Some(deps) =>
            deps
              .map { id =>
                s"""|${hasFoldButton(id)}
                    |var element$id = document.getElementById("$id")
                    |if (element$id) {
                    |  element$id.addEventListener("change",$functionName);
                    |  element$id.addEventListener("keyup",$functionName);
                    |}
                    |""".stripMargin
              }
              .mkString("\n")
        }
      componentEls + windowEl
    }

    val elementRoundingMode = roundingMode match {
      case Some(RoundingMode.Up)       => "ROUND_UP"
      case Some(RoundingMode.Down)     => "ROUND_DOWN"
      case Some(RoundingMode.Ceiling)  => "ROUND_CEIL"
      case Some(RoundingMode.Floor)    => "ROUND_FLOOR"
      case Some(RoundingMode.HalfUp)   => "ROUND_HALF_UP"
      case Some(RoundingMode.HalfDown) => "ROUND_HALF_DOWN"
      case Some(RoundingMode.HalfEven) => "ROUND_HALF_EVEN"
      case _                           => "ROUND_DOWN"
    }
    val elementId = field.id
    val functionName = JsFunction("compute" + elementId)

    s"""|function $functionName() {
        |  var result = BigNumber(${computeExpr(expr, additionIdentity)}).decimalPlaces(${roundToCtx(field)}, BigNumber.$elementRoundingMode);
        |  document.getElementById("$elementId").value = result;
        |  var total = document.getElementById("$elementId-total");
        |  if(total) total.innerHTML = ${if (field.isSterling) "displaySterling(result)" else "result"};
        |}
        |${listeners(functionName)}
        |""".stripMargin

  }

  def roundToCtx(fc: FormComponentWithCtx) = fc match {
    case FormComponentWithGroup(fc, _) => extractMaxFractionalDigits(fc).maxDigits
    case FormComponentSimple(fc)       => extractMaxFractionalDigits(fc).maxDigits
  }

  def collapsingGroupJavascript(fieldId: FormComponentId, group: Group) =
    s"""|function removeOnClick$fieldId() {
        |${group.fields.map(fv => s"""  document.getElementById("${fv.id}").value = '';""").mkString("\n")}
        |}
        |""".stripMargin
}
