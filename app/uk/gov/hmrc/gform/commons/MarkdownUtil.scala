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

package uk.gov.hmrc.gform.commons

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.twirl.api.Html
import uk.gov.hmrc.gform.sharedmodel.{ LangADT, LocalisedString }

object MarkDownUtil {

  private def addTargetToLinks(html: String): String = {
    val doc: Document = Jsoup.parse(html)
    doc
      .getElementsByAttributeValueStarting("href", "http")
      .attr("target", "_blank")
    doc.body().html()
  }

  def markDownParser(ls: LocalisedString)(implicit l: LangADT): Html = {
    val markDownText = ls.value
    val flavour = new GFMFlavourDescriptor
    val parsedTree = new MarkdownParser(flavour).buildMarkdownTreeFromString(markDownText)
    val html = new HtmlGenerator(markDownText, parsedTree, flavour, false).generateHtml
    Html(addTargetToLinks(html))
  }
}
