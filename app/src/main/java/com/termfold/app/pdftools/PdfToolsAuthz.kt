package com.termfold.app.pdftools

object PdfToolsAuthz {
    fun canMutate(authz: PdfToolsAuthzClass): Boolean =
        authz == PdfToolsAuthzClass.OWN_TEMPLATE || authz == PdfToolsAuthzClass.OWN_DATA_DOC

    fun blockReason(authz: PdfToolsAuthzClass?): String? = when (authz) {
        null -> "Choose an authorization class first."
        PdfToolsAuthzClass.OWN_TEMPLATE, PdfToolsAuthzClass.OWN_DATA_DOC -> null
        PdfToolsAuthzClass.THIRD_PARTY_ISSUED ->
            "Cannot alter a third-party issued document. Generate from a template you own, or ask the issuer for a correction."
        PdfToolsAuthzClass.UNKNOWN ->
            "Set authorization to a template or document you own before changing a PDF."
    }
}
