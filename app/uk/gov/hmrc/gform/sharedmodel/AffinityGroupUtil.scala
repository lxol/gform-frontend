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

package uk.gov.hmrc.gform.sharedmodel

import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.gform.auth.models.{ AnonymousRetrievals, AuthenticatedRetrievals, MaterialisedRetrievals }

object AffinityGroupUtil {

  def fromRetrievals(materialisedRetrievals: MaterialisedRetrievals): Option[AffinityGroup] =
    materialisedRetrievals match {
      case AnonymousRetrievals(_)     => None
      case a: AuthenticatedRetrievals => Some(a.affinityGroup)
    }

  def affinityGroupNameO(affinityGroup: Option[AffinityGroup]): String = affinityGroup.fold("")(affinityGroupName)

  def affinityGroupName(affinityGroup: AffinityGroup): String = affinityGroup match {
    case AffinityGroup.Individual   => "individual"
    case AffinityGroup.Agent        => "agent"
    case AffinityGroup.Organisation => "organisation"
  }

  def toAffinityGroupO(asStr: Option[String]): Option[AffinityGroup] =
    asStr.flatMap(toAffinityGroup)

  def toAffinityGroup(affinityGroupAsStr: String): Option[AffinityGroup] = affinityGroupAsStr match {
    case "individual"   => Some(AffinityGroup.Individual)
    case "agent"        => Some(AffinityGroup.Agent)
    case "organisation" => Some(AffinityGroup.Organisation)
    case _              => None
  }
}
