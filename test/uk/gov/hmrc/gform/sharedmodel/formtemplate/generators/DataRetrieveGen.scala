/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.gform.sharedmodel.formtemplate.generators

import org.scalacheck.Gen
import uk.gov.hmrc.gform.sharedmodel.formtemplate.generators.ExprGen.formCtxGen
import uk.gov.hmrc.gform.sharedmodel.{ DataRetrieve, DataRetrieveId }
import uk.gov.hmrc.gform.sharedmodel.DataRetrieve.ValidateBankDetails

trait DataRetrieveGen {
  def validateBankGen: Gen[ValidateBankDetails] = for {
    id                <- Gen.alphaStr
    sortCodeExpr      <- formCtxGen
    accountNumberExpr <- formCtxGen
  } yield ValidateBankDetails(DataRetrieveId(id), sortCodeExpr, accountNumberExpr)

  def dataRetrieveGen: Gen[DataRetrieve] = validateBankGen
}

object DataRetrieveGen extends DataRetrieveGen
