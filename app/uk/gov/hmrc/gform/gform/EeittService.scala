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

package uk.gov.hmrc.gform.gform

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.gform.auth.models.{ IsAgent, MaterialisedRetrievals }
import uk.gov.hmrc.gform.connectors.EeittConnector
import uk.gov.hmrc.gform.models.userdetails.GroupId
import uk.gov.hmrc.gform.sharedmodel.formtemplate._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class EeittService(eeittConnector: EeittConnector)(
  implicit ec: ExecutionContext
) {

  def getValue(eeitt: Eeitt, retrievals: MaterialisedRetrievals, formTemplate: FormTemplate)(
    implicit hc: HeaderCarrier) = {
    val regimeId = formTemplate.authConfig match {
      case EeittModule(regimeId)                                                         => regimeId
      case HmrcEnrolmentModule(EnrolmentAuth(_, DoCheck(_, _, RegimeIdCheck(regimeId)))) => regimeId
      case _                                                                             => RegimeId("")
    }
    for {
      prepopData <- (eeitt, retrievals) match {
                     case (Agent, IsAgent()) | (UserId, IsAgent()) =>
                       eeittConnector.prepopulationAgent(GroupId(retrievals)).map(_.arn)
                     case (BusinessUser, IsAgent()) =>
                       Future.successful("")
                     case (BusinessUser, _) | (UserId, _) =>
                       eeittConnector
                         .prepopulationBusinessUser(GroupId(retrievals), regimeId)
                         .map(_.registrationNumber)
                     case _ =>
                       Future.successful("")
                   }
    } yield prepopData
  }
}
