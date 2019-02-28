package uk.gov.hmrc.gform.controllers
import uk.gov.hmrc.gform.Spec
import uk.gov.hmrc.gform.controllers.helpers.FormDataHelpers
import uk.gov.hmrc.gform.sharedmodel.formtemplate.{ FormComponentId}

class FormDataHelpersSpec extends Spec {

  lazy val commas: Map[FormComponentId, Seq[String]] = Map(FormComponentId("Hello") -> Seq("1,000,000"))
  lazy val commas2: Map[FormComponentId, Seq[String]] = Map(FormComponentId("Hello") -> Seq("1,000,000"), FormComponentId("Bye") -> Seq("9,000,000"))
  lazy val noCommas: Map[FormComponentId, Seq[String]] = Map(FormComponentId("Hello") -> Seq("1000000"))
  lazy val noCommas2: Map[FormComponentId, Seq[String]] = Map(FormComponentId("Hello") -> Seq("1000000"), FormComponentId("Bye") -> Seq("9000000"))

  "removeCommas" should "leave a string without commas untouched" in {
    FormDataHelpers.removeCommas(noCommas) should be(noCommas)
  }
  it should "remove commas from a string" in {
    FormDataHelpers.removeCommas(commas) should be(noCommas)
  }
  it should "leave multiple strings without commas untouched" in {
    FormDataHelpers.removeCommas(noCommas2) should be(noCommas2)
  }
  "removeCommas" should "remove commas from multiple strings" in {
    FormDataHelpers.removeCommas(commas2) should be(noCommas2)
  }
}