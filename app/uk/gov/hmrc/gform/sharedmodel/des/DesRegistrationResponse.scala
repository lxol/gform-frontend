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

package uk.gov.hmrc.gform.sharedmodel.des

import cats.Eq
import julienrf.json.derived
import play.api.libs.json._

sealed trait Address extends Product with Serializable

object Address {
  implicit val format: OFormat[Address] = derived.oformat
}

case class UkAddress(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  addressLine4: Option[String],
  postalCode: Option[String]
) extends Address {
  val countryCode = "GB"
}

case class InternationalAddress(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  addressLine4: Option[String],
  countryCode: String,
  postalCode: Option[String]
) extends Address

object InternationalAddress {
  implicit val format: OFormat[InternationalAddress] = Json.format[InternationalAddress]
}
object UkAddress {
  implicit val format: OFormat[UkAddress] = Json.format[UkAddress]
}

case class ContactDetails(
  primaryPhoneNumber: Option[String],
  secondaryPhoneNumber: Option[String],
  faxNumber: Option[String],
  emailAddress: Option[String]
)
object ContactDetails {
  implicit val format: OFormat[ContactDetails] = Json.format[ContactDetails]
}

sealed trait DesEntity {
  def getOrganisationName = this match {
    case Organisation(organisationName, _, _) => organisationName
    case Individual(firstName, lastName, _)   => firstName + lastName.fold("")(" " + _)
  }
  def getOrganisationType = this match {
    case Organisation(_, _, organisationType) => organisationType
    case _                                    => ""
  }
  def getIsAGroup = this match {
    case Organisation(_, isAGroup, _) => if (isAGroup) "true" else "false"
    case _                            => ""
  }
}

object DesEntity {
  implicit val format: OFormat[DesEntity] = derived.oformat
}

case class Organisation(
  organisationName: String,
  isAGroup: Boolean,
  organisationType: String
) extends DesEntity
object Organisation {
  implicit val format: OFormat[Organisation] = Json.format[Organisation]
}

case class Individual(firstName: String, lastName: Option[String], dateOfBirth: Option[String]) extends DesEntity

object Individual {
  implicit val format: OFormat[Individual] = Json.format[Individual]
}

case class DesRegistrationResponse(
  safeId: String,
  agentReferenceNumber: Option[String],
  sapNumber: String,
  isEditable: Boolean,
  isAnAgent: Boolean,
  isAnASAgent: Boolean,
  isAnIndividual: Boolean,
  orgOrInd: DesEntity,
  address: Address,
  contactDetails: Option[ContactDetails]
)

object DesRegistrationResponse {
  implicit val catsEq: Eq[DesRegistrationResponse] = Eq.fromUniversalEquals
  implicit val format: OFormat[DesRegistrationResponse] = Json.format[DesRegistrationResponse]
}
