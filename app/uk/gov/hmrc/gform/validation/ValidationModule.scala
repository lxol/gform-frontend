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

package uk.gov.hmrc.gform.validation

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.gform.fileupload.FileUploadModule
import uk.gov.hmrc.gform.gformbackend.GformBackendModule
import uk.gov.hmrc.gform.graph.GraphModule
import uk.gov.hmrc.gform.lookup.LookupRegistry
import uk.gov.hmrc.gform.playcomponents.PlayBuiltInsModule

class ValidationModule(
  fileUploadModule: FileUploadModule,
  gformBackendModule: GformBackendModule,
  graphModule: GraphModule,
  lookupRegistry: LookupRegistry,
  playBuiltInsModule: PlayBuiltInsModule
)(
  implicit ec: ExecutionContext
) {

  val validationService = new ValidationService(
    fileUploadModule.fileUploadService,
    gformBackendModule.gformConnector,
    graphModule.booleanExprEval,
    lookupRegistry,
    graphModule.recalculation,
    playBuiltInsModule.i18nSupport
  )
}
