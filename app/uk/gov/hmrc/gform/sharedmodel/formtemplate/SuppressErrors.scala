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

sealed trait SuppressErrors extends Product with Serializable {
  def asString: String = this match {
    case SeYes => SuppressErrors.seYes
    case SeNo  => SuppressErrors.seNo
  }
}

case object SeYes extends SuppressErrors
case object SeNo extends SuppressErrors

object SuppressErrors {

  val seYes = "t"
  val seNo = "f"

  def apply(b: Boolean) = if (b) SeYes else SeNo

}
