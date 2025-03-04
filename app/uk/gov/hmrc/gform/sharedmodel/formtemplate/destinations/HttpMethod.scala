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

package uk.gov.hmrc.gform.sharedmodel.formtemplate.destinations
import play.api.libs.json.Format
import uk.gov.hmrc.gform.sharedmodel.formtemplate.ADTFormat

sealed trait HttpMethod extends Product with Serializable

object HttpMethod {
  case object GET extends HttpMethod
  case object POST extends HttpMethod
  case object PUT extends HttpMethod

  implicit val format: Format[HttpMethod] = ADTFormat
    .formatEnumeration("GET" -> GET, "POST" -> POST, "PUT" -> PUT)
}
