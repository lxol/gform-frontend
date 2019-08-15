package uk.gov.hmrc.gform.models

import java.time.LocalDateTime
import uk.gov.hmrc.gform.sharedmodel.AccessCode

case class AccessCodeLink (
                            accessCode: AccessCode,
                            createdDate: LocalDateTime,
                            lastModifiedDate: LocalDateTime
)
