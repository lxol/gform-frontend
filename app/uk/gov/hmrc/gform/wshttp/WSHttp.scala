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

package uk.gov.hmrc.gform.wshttp

import akka.actor.ActorSystem
import com.typesafe.config.Config
import play.api.{ Configuration, Play }
import uk.gov.hmrc.gform.InjectionDodge
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.config.LoadAuditingConfig
import uk.gov.hmrc.play.http.ws._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.AppName

object MicroserviceAuditConnector extends AuditConnector {
  lazy val auditingConfig: AuditingConfig = LoadAuditingConfig(s"auditing")
}

trait Hooks extends HttpHooks with HttpAuditing {
  override val hooks = Seq(AuditingHook)
  override lazy val auditConnector: AuditConnector = MicroserviceAuditConnector
}

trait WSHttp
    extends HttpGet with WSGet with HttpPut with WSPut with HttpPost with WSPost with HttpDelete with WSDelete
    with Hooks with AppName {
  override protected def actorSystem: ActorSystem = InjectionDodge.actorSystem
  override protected def configuration: Option[Config] = Option(InjectionDodge.configuration.underlying)
  override protected def appNameConfiguration: Configuration = InjectionDodge.configuration
}
object WSHttp extends WSHttp
