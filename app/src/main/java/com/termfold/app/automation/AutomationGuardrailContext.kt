package com.termfold.app.automation

import java.io.File

/** Live guardrail policy plus the constraints file used for protected writes. */
data class AutomationGuardrailContext(
    val policy: AutomationGuardrailPolicy,
    val constraintsFile: File?,
)
