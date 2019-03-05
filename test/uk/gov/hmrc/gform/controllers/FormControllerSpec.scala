package uk.gov.hmrc.gform.controllers
import com.sun.tools.javac.comp.Enter
import org.scalatest.PrivateMethodTester
import uk.gov.hmrc.gform.Spec
import uk.gov.hmrc.gform.config.{AppConfig, FrontendAppConfig}
import uk.gov.hmrc.gform.gform.{FormController, SectionRenderingService}
import org.scalatest.mockito.MockitoSugar.mock
import play.api.i18n.I18nSupport
import uk.gov.hmrc.gform.controllers.helpers.FormDataHelpers
import uk.gov.hmrc.gform.fileupload.FileUploadService
import uk.gov.hmrc.gform.gformbackend.GformConnector
import uk.gov.hmrc.gform.models.ProcessDataService
import uk.gov.hmrc.gform.obligation.ObligationService
import uk.gov.hmrc.gform.sharedmodel.form.{FormData, FormDataRecalculated}
import uk.gov.hmrc.gform.sharedmodel.formtemplate._
import uk.gov.hmrc.gform.validation.ValidationService

import scala.concurrent.Future

class FormControllerSpec extends Spec with PrivateMethodTester {

  val mockAppConfig =                     mock[AppConfig]
  val mockFrontednAppConfig =             mock[FrontendAppConfig]
  val mockI18nSupport =                   mock[I18nSupport]
  val mockAuthenticationRequestActions =  mock[AuthenticatedRequestActions]
  val mockFileUploadService =             mock[FileUploadService]
  val mockValidationService =             mock[ValidationService]
  val mockSectionRenderingService =       mock[SectionRenderingService]
  val mockGformConnector =                mock[GformConnector]
  val mockProcessDataService =            mock[ProcessDataService[Future, Throwable]]
  val mockObligationService =             mock[ObligationService]

  val formController = new FormController(mockAppConfig,
    mockFrontednAppConfig,
    mockI18nSupport,
    mockAuthenticationRequestActions,
    mockFileUploadService,
    mockValidationService,
    mockSectionRenderingService,
    mockGformConnector,
    mockProcessDataService,
    mockObligationService)

//  val a = List(Section("Calculate your Bingo Duty",
//    None,None,None,None,None,None,None,
//    List(FormComponent(amountDueAsPayments,Text(Sterling(Down),Value,DEFAULT),
//      "Enter an amount due for playing bingo in the return period"
//      ,Some("This is the total value of your receipts from bingo games including bingo cards, participation fees and all stakes."),Some("Total amount for playing bingo"),None,true,true,true,false,false,Some("Enter the amount due as payments"),None)),None,None), Section("Tell us about your expenses",None,None,None,None,None,None,None,List(FormComponent("amountSpentWinning",Text(Sterling(Down),Value,DEFAULT),"How much did you spend on winnings in the return period?",Some("This is the total value of all winnings from money prizes and vouchers that includes VAT."),Some("Amount spent on winnings"),None,true,true,true,false,false,Some("Enter the amount spent on winnings in the return period"),None)),None,None), Section("Have you any losses from your last return",None,None,None,None,None,None,None,List(FormComponent(yesNoLosses,Choice(YesNo,NonEmptyList(Yes, No),Horizontal,List(),None),Do you have losses to carry forward from your previous return?,None,Some(Losses from last return),None,true,true,true,false,false,Some(Select yes or no if you have losses from your previous return),None)),None,None), Section(Tell us about any losses from your last return,None,None,None,Some(IncludeIf(Equals(FormCtx(yesNoLosses),Constant(0)))),None,None,None,List(FormComponent(bingoLossesCarried,Text(Sterling(Down),Value,DEFAULT),Enter any losses declared from your previous return ,Some(Enter any losses you declared in the previous accounting period.),Some(Losses declared from previous return),None,true,true,true,false,false,Some(Enter any losses declared from previous returns),None)),None,None), Section(Total expenses on bingo winnings and losses,None,None,None,Some(IncludeIf(Equals(FormCtx(yesNoLosses),Constant(0)))),None,None,None,List(FormComponent(totalExpen,Text(Sterling(Down),Add(FormCtx(amountSpentWinning),FormCtx(bingoLossesCarried)),DEFAULT),Total amount spent on bingo winnings and losses claimed in this return,None,Some(Expenses on bingo winnings),None,true,false,true,false,false,None,Some(List(TotalValue)))),None,None), Section(Total expenses on bingo winnings and losses,None,None,None,Some(IncludeIf(Equals(FormCtx(yesNoLosses),Constant(1)))),None,None,None,List(FormComponent(totalExpenNoLosses,Text(Sterling(Down),FormCtx(amountSpentWinning),DEFAULT),Total amount spent on bingo winnings and losses claimed in this return,None,Some(Expenses on bingo winnings),None,true,false,true,false,false,None,Some(List(TotalValue)))),None,None), Section(Bingo Duty profits in the return period,None,None,None,Some(IncludeIf(Equals(FormCtx(yesNoLosses),Constant(0)))),None,None,None,List(FormComponent(bingoProfitsLosses,Text(Sterling(Down),Subtraction(FormCtx(amountDueAsPayments),FormCtx(totalExpen)),DEFAULT),Your Bingo Duty profits,None,Some(Bingo Duty profits),None,true,false,true,false,false,None,Some(List(TotalValue))), FormComponent(dutyDueBingoProfitsLosses,Text(Sterling(Down),Multiply(FormCtx(bingoProfitsLosses),Constant(0.1)),DEFAULT),Duty due on your Bingo Duty profits,None,Some(Duty due on Bingo Duty profits),None,true,false,true,false,false,None,Some(List(TotalValue)))),None,None), Section(Bingo Duty profits in the return period,None,None,None,Some(IncludeIf(Equals(FormCtx(yesNoLosses),Constant(1)))),None,None,None,List(FormComponent(bingoProfitsNoLosses,Text(Sterling(Down),Subtraction(FormCtx(amountDueAsPayments),FormCtx(totalExpenNoLosses)),DEFAULT),Your Bingo Duty profits,None,Some(Bingo Duty profits),None,true,false,true,false,false,None,Some(List(TotalValue))), FormComponent(dutyDueBingoProfitsNoLosses,Text(Sterling(Down),Multiply(FormCtx(bingoProfitsNoLosses),Constant(0.1)),DEFAULT),Duty due on your Bingo Duty profits,None,Some(Duty due on Bingo Duty profits),None,true,false,true,false,false,None,Some(List(TotalValue)))),None,None), Section(Under-declarations from your previous return,None,None,None,None,None,None,None,List(FormComponent(yesNoUnderDec,Choice(YesNo,NonEmptyList(Yes, No),Horizontal,List(),None),Did you declare too little duty on your previous return?,None,Some(Under-declarations from previous return),None,true,true,true,false,false,Some(Select yes or no if you have declared too little duty on your previous return),None)),None,None), Section(Tell us about your under-declarations,None,None,None,Some(IncludeIf(Equals(FormCtx(yesNoUnderDec),Constant(0)))),None,None,None,List(FormComponent(bingoUnderDec,Text(Sterling(Down),Value,DEFAULT),Enter the amount under-declared,None,Some(Under-declaration from previous return),None,true,true,true,false,false,Some(Enter the amount under-declared),None)),None,None), Section(Losses to carry forward to your next return,None,None,None,Some(IncludeIf(And(And(Equals(FormCtx(yesNoLosses),Constant(0)),Equals(FormCtx(yesNoUnderDec),Constant(0))),LessThan(Subtraction(FormCtx(bingoProfitsLosses),FormCtx(bingoUnderDec)),Constant(0))))),None,None,None,List(FormComponent(totalAmountLossesUnderCarryForward,Text(Sterling(Down),Subtraction(FormCtx(bingoProfitsLosses),FormCtx(bingoUnderDec)),DEFAULT),Losses to carry forward,None,None,None,true,false,true,false,false,None,Some(List(TotalValue)))),None,None), Section(Bingo Duty to pay in this return,None,None,None,Some(IncludeIf(And(And(Equals(FormCtx(yesNoLosses),Constant(0)),Equals(FormCtx(yesNoUnderDec),Constant(0))),GreaterThanOrEquals(Subtraction(FormCtx(bingoProfitsLosses),FormCtx(bingoUnderDec)),Constant(0))))),None,None,None,List(FormComponent(totalAmountToPayLossesUnderDec,Text(Sterling(Down),Add(Multiply(FormCtx(bingoProfitsLosses),Constant(0.10)),FormCtx(bingoUnderDec)),DEFAULT),Bingo Duty to pay,None,None,None,true,false,true,false,false,None,Some(List(TotalValue))), FormComponent(basedOnDutyDueFour,InformationMessage(NoFormat,This amount is based on the 10% Bingo Duty rate),,None,None,None,true,false,false,false,false,None,None)),None,None), Section(Bingo Duty to pay in this return,None,None,None,Some(IncludeIf(And(And(Equals(FormCtx(yesNoLosses),Constant(0)),Equals(FormCtx(yesNoUnderDec),Constant(1))),LessThan(FormCtx(bingoProfitsLosses),Constant(0))))),None,None,None,List(FormComponent(totalAmountLossesNoUnderCarryForward,Text(Sterling(Down),FormCtx(bingoProfitsLosses),DEFAULT),Losses to carry forward,None,None,None,true,false,true,false,false,None,Some(List(TotalValue)))),None,None), Section(Bingo Duty to pay in this return,None,None,None,Some(IncludeIf(And(And(Equals(FormCtx(yesNoLosses),Constant(0)),Equals(FormCtx(yesNoUnderDec),Constant(1))),GreaterThanOrEquals(FormCtx(bingoProfitsLosses),Constant(0))))),None,None,None,List(FormComponent(totalAmountLossesNoUnderDue,Text(Sterling(Down),Multiply(FormCtx(bingoProfitsLosses),Constant(0.10)),DEFAULT),Bingo Duty to pay,None,None,None,true,false,true,false,false,None,Some(List(TotalValue))), FormComponent(basedOnDutyDueOne,InformationMessage(NoFormat,This amount is based on the 10% Bingo Duty rate),,None,None,None,true,false,false,false,false,None,None)),None,None), Section(Losses to carry forward to your next return,None,None,None,Some(IncludeIf(And(And(Equals(FormCtx(yesNoLosses),Constant(1)),Equals(FormCtx(yesNoUnderDec),Constant(1))),LessThan(FormCtx(bingoProfitsNoLosses),Constant(0))))),None,None,None,List(FormComponent(totalAmountNoLossesNoUnderCarryForward,Text(Sterling(Down),FormCtx(bingoProfitsNoLosses),DEFAULT),Your Bingo Duty to carry forward,None,None,None,true,false,true,false,false,None,Some(List(TotalValue)))),None,None), Section(Bingo Duty to pay in this return,None,None,None,Some(IncludeIf(And(And(Equals(FormCtx(yesNoLosses),Constant(1)),Equals(FormCtx(yesNoUnderDec),Constant(1))),GreaterThanOrEquals(FormCtx(bingoProfitsNoLosses),Constant(0))))),None,None,None,List(FormComponent(totalAmountNoLossesNoUnderDutyDue,Text(Sterling(Down),Multiply(FormCtx(bingoProfitsNoLosses),Constant(0.10)),DEFAULT),Bingo Duty to pay,None,None,None,true,false,true,false,false,None,Some(List(TotalValue))), FormComponent(basedOnDutyDueThree,InformationMessage(NoFormat,This amount is based on the 10% Bingo Duty rate),,None,None,None,true,false,false,false,false,None,None)),None,None), Section(Bingo Duty to pay in this return,None,None,None,Some(IncludeIf(And(And(Equals(FormCtx(yesNoLosses),Constant(1)),Equals(FormCtx(yesNoUnderDec),Constant(0))),GreaterThanOrEquals(Subtraction(FormCtx(bingoProfitsNoLosses),FormCtx(bingoUnderDec)),Constant(0))))),None,None,None,List(FormComponent(totalAmountNoLossesUnder,Text(Sterling(Down),Add(Multiply(FormCtx(bingoProfitsNoLosses),Constant(0.1)),FormCtx(bingoUnderDec)),DEFAULT),Bingo Duty to pay,None,None,None,true,false,true,false,false,None,Some(List(TotalValue))), FormComponent(basedOnDutyDueTwo,InformationMessage(NoFormat,This amount is based on the 10% Bingo Duty rate),,None,None,None,true,false,false,false,false,None,None)),None,None), Section(Losses to carry forward to your next return,None,None,None,Some(IncludeIf(And(And(Equals(FormCtx(yesNoLosses),Constant(1)),Equals(FormCtx(yesNoUnderDec),Constant(0))),LessThan(Subtraction(FormCtx(bingoProfitsNoLosses),FormCtx(bingoUnderDec)),Constant(0))))),None,None,None,List(FormComponent("totalAmountNoLossesUnderCarryForward,Text(Sterling(Down),Subtraction(FormCtx("bingoProfitsNoLosses"),FormCtx("bingoUnderDec")),DEFAULT),"Your Bingo Duty to carry forward",None,None,None,true,false,true,false,false,None,Some(List(TotalValue)))),None,None))




    "validateForm" should "leave a string without commas untouched" in {
      val mockFormDataRecalculated =             mock[FormDataRecalculated]
      val mockSectionList =             mock[List[Section]]
      val mockSectionNumber =             mock[SectionNumber]
      val mockObligationServicea =             mock[AuthCacheWithForm]
    val validateFormMethod = PrivateMethod[Future[(Boolean, FormData)]]('validateForm)
//
    formController invokePrivate validateFormMethod(mockFormDataRecalculated, mockSectionList, mockSectionNumber, mockObligationServicea) shouldBe  "asdasda"
//
  }

}
