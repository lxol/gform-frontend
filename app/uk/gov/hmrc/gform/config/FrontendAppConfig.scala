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
import play.api.i18n.Lang

case class FrontendAppConfig(
  albAdminIssuerUrl: String,
  assetsPrefix: String,
  analyticsToken: String,
  analyticsHost: String,
  reportAProblemPartialUrl: String,
  reportAProblemNonJSUrl: String,
  governmentGatewaySignInUrl: String,
  gformFrontendBaseUrl: String,
  betaFeedbackUrlNoAuth: String,
  signOutUrl: String,
  whitelistEnabled: Boolean,
  googleTagManagerIdAvailable: Boolean,
  googleTagManagerId: String,
  authModule: AuthModule,
  availableLanguages: Map[String, Lang],
  routeToSwitchLanguage: String => play.api.mvc.Call
)
