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

package uk.gov.hmrc.gform.config

import uk.gov.hmrc.gform.Spec

case class SampleConf(fooFoo: Int, bar: String)

class AppConfigSpec extends Spec {

  behavior of "AppConfig"

  it should "be loadable" in {
    val appConfig = AppConfig.loadOrThrow()
    appConfig.appName shouldBe "gform-frontend"
    appConfig.`google-analytics` shouldBe GoogleAnalytics("N/A", "auto")
    appConfig.`government-gateway-sign-in-url` shouldBe "http://localhost:9949/auth-login-stub/gg-sign-in"
    appConfig.`gform-frontend-base-url` shouldBe "http://localhost"
  }

}
