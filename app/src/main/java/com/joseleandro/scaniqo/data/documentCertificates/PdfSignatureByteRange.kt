package com.joseleandro.scaniqo.data.documentCertificates

/** Rejects malformed ranges before they reach PDFBox or the CMS parser. */
internal data class PdfSignatureByteRange(val firstLength: Int, val secondOffset: Int, val secondLength: Int) {
    val signedRevisionLength: Long get() = secondOffset.toLong() + secondLength
    fun covers(fileLength: Long) = signedRevisionLength == fileLength

    companion object {
        fun parse(values: IntArray?, fileLength: Long): PdfSignatureByteRange? {
            if (values == null) return null
            if (values.size != 4 || values[0] != 0 || values[1] <= 0 ||
                values[2].toLong() <= values[1].toLong() + 1 || values[3] <= 0
            ) return null
            if (values[2].toLong() + values[3] > fileLength) return null
            return PdfSignatureByteRange(values[1], values[2], values[3])
        }
    }
}
