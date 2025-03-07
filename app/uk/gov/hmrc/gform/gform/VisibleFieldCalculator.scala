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

package uk.gov.hmrc.gform.gform

import uk.gov.hmrc.gform.keystore.RepeatingComponentService
import uk.gov.hmrc.gform.models.ExpandUtils.submittedFCs
import uk.gov.hmrc.gform.sharedmodel.form.{ FormData, FormDataRecalculated, FormField }
import uk.gov.hmrc.gform.sharedmodel.formtemplate._

object VisibleFieldCalculator {
  def apply(template: FormTemplate, data: FormData, formDataRecalculated: FormDataRecalculated): Seq[FormField] = {
    val allSections = RepeatingComponentService.getAllSections(template, formDataRecalculated)
    val visibleSections = allSections.filter(formDataRecalculated.isVisible)
    val formComponentsInVisibleSections =
      visibleSections.flatMap(_.expandSectionRc(formDataRecalculated.data).allFCs) :::
        template.declarationSection.fields :::
        template.acknowledgementSection.fields

    val visibleFormComponents: List[FormComponent] = submittedFCs(
      formDataRecalculated,
      formComponentsInVisibleSections
    )

    val visibleFormComponentIds: Set[FormComponentId] = visibleFormComponents.flatMap { component =>
      component match {
        case fc @ IsMultiField(mf) => component.id :: mf.fields(fc.id).toList
        case _                     => List(component.id)
      }
    }.toSet

    data.fields.filter { field =>
      visibleFormComponentIds(field.id)
    }
  }
}
