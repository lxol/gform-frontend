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

package uk.gov.hmrc.gform.sharedmodel.formtemplate.generators
import org.scalacheck.Gen
import uk.gov.hmrc.gform.sharedmodel.formtemplate.{ After, AnyDate, AnyDay, AnyMonth, AnyText, AnyYear, BasicText, Before, BeforeAfterPrecisely, CompanyRegistrationNumber, ConcreteDate, CountryCode, DateConstraint, DateConstraintInfo, DateConstraintType, DateConstraints, DateField, EORI, Email, ExactDay, ExactMonth, ExactYear, FirstDay, LastDay, NINO, Next, NonUkCountryCode, Number, OffsetDate, PositiveNumber, Precisely, Previous, RoundingMode, ShortText, Sterling, TelephoneNumber, TextConstraint, TextExpression, TextWithRestrictions, Today, UTR, UkBankAccountNumber, UkSortCodeFormat, UkVrn }

trait FormatExprGen {
  def numberGen: Gen[Number] =
    for {
      maxWholeDigits      <- Gen.posNum[Int]
      maxFractionalDigits <- Gen.posNum[Int]
      round               <- FormatExprGen.roundingModeGen
      units               <- Gen.option(PrimitiveGen.nonEmptyAlphaNumStrGen)
    } yield Number(maxWholeDigits, maxFractionalDigits, round, units)

  def positiveNumberGen: Gen[PositiveNumber] =
    for {
      maxWholeDigits      <- Gen.posNum[Int]
      maxFractionalDigits <- Gen.posNum[Int]
      round               <- FormatExprGen.roundingModeGen
      units               <- Gen.option(PrimitiveGen.nonEmptyAlphaNumStrGen)
    } yield PositiveNumber(maxWholeDigits, maxFractionalDigits, round, units)

  def textWithRestrictions: Gen[TextWithRestrictions] =
    for {
      min <- Gen.posNum[Int]
      max <- Gen.posNum[Int]
    } yield TextWithRestrictions(min, max)

  def shortText: Gen[ShortText] =
    for {
      min <- Gen.posNum[Int]
      max <- Gen.posNum[Int]
    } yield ShortText(min, max)

  def sterlingGen: Gen[Sterling] =
    PrimitiveGen.booleanGen.map(b => Sterling(RoundingMode.defaultRoundingMode, b))

  def textConstraintGen: Gen[TextConstraint] = Gen.oneOf(
    Gen.const(AnyText),
    numberGen,
    positiveNumberGen,
    Gen.const(BasicText),
    shortText,
    textWithRestrictions,
    sterlingGen,
    Gen.const(UkBankAccountNumber),
    Gen.const(UkSortCodeFormat),
    Gen.const(UTR),
    Gen.const(NINO),
    Gen.const(TelephoneNumber),
    Gen.const(Email),
    Gen.const(UkVrn),
    Gen.const(CountryCode),
    Gen.const(NonUkCountryCode),
    Gen.const(CompanyRegistrationNumber),
    Gen.const(EORI)
  )

  def telephoneNumberGen(phoneNumberType: PhoneNumberType): Gen[String] =
    for {
      phoneNumberRange <- Gen.choose(7, 25)
      elems            <- telephoneNumberHelper(phoneNumberRange, phoneNumberType)

    } yield elems.mkString

  def textExpressionGen: Gen[TextExpression] = ExprGen.exprGen().map(TextExpression(_))

  def beforeAfterPreciselyGen: Gen[BeforeAfterPrecisely] = Gen.oneOf(Before, After, Precisely)

  def exactYearGen: Gen[ExactYear] = Gen.posNum[Int].map(ExactYear)

  def exactMonthGen: Gen[ExactMonth] = Gen.posNum[Int].map(ExactMonth)

  def exactDayGen: Gen[ExactDay] = Gen.posNum[Int].map(ExactDay)

  def concreteDateGen: Gen[ConcreteDate] =
    for {
      year  <- Gen.oneOf(exactYearGen, Gen.const(AnyYear), Gen.const(Next), Gen.const(Previous))
      month <- Gen.oneOf(exactMonthGen, Gen.const(AnyMonth))
      day   <- Gen.oneOf(exactDayGen, Gen.const(AnyDay), Gen.const(FirstDay), Gen.const(LastDay))
    } yield ConcreteDate(year, month, day)

  def dateFieldGen: Gen[DateField] = FormComponentGen.formComponentIdGen.map(DateField)

  def offsetDateGen: Gen[OffsetDate] = Gen.posNum[Int].map(OffsetDate(_))

  def dateConstraintInfoGen: Gen[DateConstraintInfo] = Gen.oneOf(
    Gen.const(Today),
    concreteDateGen,
    dateFieldGen
  )

  def dateConstraintGen: Gen[DateConstraint] =
    for {
      beforeAfterPrecisely <- beforeAfterPreciselyGen
      format               <- dateConstraintInfoGen
      offset               <- offsetDateGen
    } yield DateConstraint(beforeAfterPrecisely, format, offset)

  def dateConstraintTypeGen: Gen[DateConstraintType] = Gen.oneOf(
    Gen.const(AnyDate),
    Gen.listOfN(2, dateConstraintGen).map(DateConstraints)
  )

  def roundingModeGen: Gen[RoundingMode] = Gen.oneOf(
    RoundingMode.HalfEven,
    RoundingMode.Ceiling,
    RoundingMode.Floor,
    RoundingMode.HalfUp,
    RoundingMode.HalfDown,
    RoundingMode.Down,
    RoundingMode.Up
  )

  private def telephoneNumberHelper(lengthRestraint: Int, phoneNumberType: PhoneNumberType) =
    Gen.listOfN(lengthRestraint, Gen.numChar).map {
      case phoneNumber if phoneNumberType == International => '+' :: phoneNumber
      case phoneNumber                                     => phoneNumber
    }

  sealed trait PhoneNumberType
  case object International extends PhoneNumberType
  case object UK extends PhoneNumberType
}

object FormatExprGen extends FormatExprGen
