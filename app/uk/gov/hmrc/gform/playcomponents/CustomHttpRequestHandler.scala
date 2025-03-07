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

package uk.gov.hmrc.gform.playcomponents

import play.api.http.{ DefaultHttpRequestHandler, HttpConfiguration, HttpErrorHandler }
import play.api.mvc.{ EssentialFilter, Handler, RequestHeader }
import play.api.routing.Router

class CustomHttpRequestHandler(
  router: Router,
  httpErrorHandler: HttpErrorHandler,
  httpConfiguration: HttpConfiguration,
  httpFilters: Seq[EssentialFilter]
) extends DefaultHttpRequestHandler(router, httpErrorHandler, httpConfiguration, httpFilters: _*) {

  //removing of trailing slashes
  override def routeRequest(request: RequestHeader): Option[Handler] =
    router.handlerFor(request).orElse {
      Some(request.path).filter(_.endsWith("/")).flatMap(p => router.handlerFor(request.copy(path = p.dropRight(1))))
    }
}
