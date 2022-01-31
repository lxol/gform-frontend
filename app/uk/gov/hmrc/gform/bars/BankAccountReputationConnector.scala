/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.gform.bars

import org.slf4j.{ Logger, LoggerFactory }
import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.gform.sharedmodel.formtemplate.FormTemplateId
import uk.gov.hmrc.gform.sharedmodel.{ CannotRetrieveResponse, ServiceCallResponse, ServiceResponse }
import uk.gov.hmrc.gform.wshttp.WSHttp
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.higherKinds

trait BankAccountReputationConnector[F[_]] {
  def validateBankDetails(
    formTemplateId: FormTemplateId,
    account: ValidateBankDetails.Request
  )(implicit
    hc: HeaderCarrier
  ): F[ServiceCallResponse[ValidateBankDetails.Response]]
  def businessBankAccountExistence(
    formTemplateId: FormTemplateId,
    account: BusinessBankAccountExistence.Request
  )(implicit
    hc: HeaderCarrier
  ): F[ServiceCallResponse[BusinessBankAccountExistence.Response]]
}

class BankAccountReputationAsyncConnector(ws: FormTemplateId => WSHttp, baseUrl: String)(implicit ex: ExecutionContext)
    extends BankAccountReputationConnector[Future] {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def validateBankDetails(
    formTemplateId: FormTemplateId,
    request: ValidateBankDetails.Request
  )(implicit
    hc: HeaderCarrier
  ): Future[ServiceCallResponse[ValidateBankDetails.Response]] =
    ws(formTemplateId)
      .POST[ValidateBankDetails.Request, HttpResponse](
        baseUrl + "/v2/validateBankDetails",
        request
      )
      .map { httpResponse =>
        val status = httpResponse.status
        status match {
          case 200 =>
            httpResponse.json
              .validate[ValidateBankDetails.Response]
              .fold(
                invalid => {
                  logger.error(
                    s"Calling validate bank details returned $status, but marshalling of data failed with: $invalid"
                  )
                  CannotRetrieveResponse
                },
                valid => {
                  logger.info(s"Calling validate bank details returned $status: Success.")
                  ServiceResponse(valid)
                }
              )
          case other =>
            logger.error(s"Problem when calling validate bank details. Http status: $other, body: ${httpResponse.body}")
            CannotRetrieveResponse
        }
      }
      .recover { case ex =>
        logger.error("Unknown problem when calling validate bank details", ex)
        CannotRetrieveResponse
      }

  override def businessBankAccountExistence(
    formTemplateId: FormTemplateId,
    request: BusinessBankAccountExistence.Request
  )(implicit
    hc: HeaderCarrier
  ): Future[ServiceCallResponse[BusinessBankAccountExistence.Response]] =
    ws(formTemplateId)
      .POST[BusinessBankAccountExistence.Request, HttpResponse](
        baseUrl + "/verify/business",
        request
      )
      .map { httpResponse =>
        val status = httpResponse.status
        status match {
          case 200 =>
            httpResponse.json
              .validate[BusinessBankAccountExistence.Response]
              .fold(
                invalid => {
                  logger.error(
                    s"Calling business bank account existence returned $status, but marshalling of data failed with: $invalid"
                  )
                  CannotRetrieveResponse
                },
                valid => {
                  logger.info(s"Calling business bank account existence returned $status: Success.")
                  ServiceResponse(valid)
                }
              )
          case other =>
            logger.error(
              s"Problem when calling business bank account existence. Http status: $other, body: ${httpResponse.body}"
            )
            CannotRetrieveResponse
        }
      }
      .recover { case ex =>
        logger.error("Unknown problem when calling business bank account existence", ex)
        CannotRetrieveResponse
      }
}

object ValidateBankDetails {

  def create(
    sortCode: String,
    accountNumber: String
  ) = Request(Account(sortCode, accountNumber))

  case class Account(
    sortCode: String, // The bank sort code, 6 characters long (whitespace and/or dashes should be removed)
    accountNumber: String // The bank account number, 8 characters long
  )

  object Account {
    implicit val format: Format[Account] = Json.format[Account]
  }

  case class Request(
    account: Account
  )

  object Request {
    implicit val format: Format[Request] = Json.format[Request]
  }

  case class Response(
    accountNumberWithSortCodeIsValid: String,
    nonStandardAccountDetailsRequiredForBacs: String,
    sortCodeIsPresentOnEISCD: String,
    supportsBACS: Option[String],
    ddiVoucherFlag: Option[String],
    directDebitsDisallowed: Option[String],
    directDebitInstructionsDisallowed: Option[String],
    iban: Option[String],
    sortCodeBankName: Option[String]
  )

  object Response {
    implicit val format: Format[Response] = Json.format[Response]
  }
}

object BusinessBankAccountExistence {

  def create(
    sortCode: String,
    accountNumber: String,
    companyName: String
  ) = Request(Account(sortCode, accountNumber), Some(Business(companyName, None, None)))

  case class Account(
    sortCode: String, // The bank sort code, 6 characters long (whitespace and/or dashes should be removed)
    accountNumber: String // The bank account number, 8 characters long
  )

  object Account {
    implicit val format: Format[Account] = Json.format[Account]
  }

  case class Address(
    lines: List[String], // One to four lines; cumulative length must be between 1 and 140 characters.
    town: Option[String], // Must be between 1 and 35 characters long
    postcode: Option[String]
  )

  object Address {
    implicit val format: Format[Address] = Json.format[Address]
  }

  case class Business(
    companyName: String, // Must be between 1 and 70 characters long
    companyRegistrationNumber: Option[String],
    address: Option[Address]
  )

  object Business {
    implicit val format: Format[Business] = Json.format[Business]
  }

  case class Request(
    account: Account,
    business: Option[Business]
  )

  object Request {
    implicit val format: Format[Request] = Json.format[Request]
  }
  case class Response(
    accountNumberIsWellFormatted: String,
    sortCodeIsPresentOnEISCD: String,
    sortCodeBankName: Option[String],
    nonStandardAccountDetailsRequiredForBacs: String,
    accountExists: String,
    nameMatches: String,
    sortCodeSupportsDirectDebit: String,
    sortCodeSupportsDirectCredit: String
  )

  object Response {
    implicit val format: Format[Response] = Json.format[Response]
  }
}
