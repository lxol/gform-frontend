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

package uk.gov.hmrc.gform.auditing

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.gform.akka.AkkaModule
import uk.gov.hmrc.gform.config.ConfigModule
import uk.gov.hmrc.gform.playcomponents.PlayBuiltInsModule
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.config.LoadAuditingConfig
import uk.gov.hmrc.http.hooks.HttpHook

class AuditingModule(configModule: ConfigModule, akkaModule: AkkaModule, playBuiltInsModule: PlayBuiltInsModule)(
  implicit ec: ExecutionContext
) {
  self =>

  lazy val auditConnector: AuditConnector = new AuditConnector {
    //WARN: LoadAuditingConfig uses play deprecations.
    //Thus you can not instantiate this class if play application is not running
    override def auditingConfig: AuditingConfig = LoadAuditingConfig(s"auditing")

  }

  lazy val httpAuditing: HttpAuditing = new HttpAuditing {
    override def auditConnector: AuditConnector = self.auditConnector
    override def appName: String = configModule.appConfig.appName
  }

  lazy val httpAuditingHook: HttpHook = httpAuditing.AuditingHook

  lazy val auditService = new AuditService {
    override def auditConnector = self.auditConnector
  }

  val httpAuditingService: HttpAuditingService = new HttpAuditingService(configModule.appConfig.appName, auditConnector)
}
