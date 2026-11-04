package com.joseleandro.scaniqo.data.documentCertificates

import com.joseleandro.scaniqo.domain.model.PdfSignatureIntegrity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.CertificateExpiredException
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfCertificateCryptoTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun signsAndVerifiesTheExactPdfBytesWithAnRsaCertificate() {
        val keys = keyPair()
        val file = signedPdf(keys, certificate(keys))
        PDDocument.load(file).use { pdf ->
            val result = PdfCertificateCrypto.inspect(file, pdf).single()
            assertEquals(PdfSignatureIntegrity.VERIFIED, result.integrity)
            assertTrue(result.coversWholeDocument)
            assertTrue(result.certificateCurrent)
            assertTrue(result.signer.contains("Teste Scaniqo"))
        }
    }

    @Test
    fun changingOneSignedValueInvalidatesTheSignatureEvenWhenPdfStillOpens() {
        val keys = keyPair()
        val file = signedPdf(keys, certificate(keys))
        val marker = file.readBytes().toString(Charsets.ISO_8859_1).indexOf("ORIGINAL MARKER")
        assertTrue(marker >= 0)
        RandomAccessFile(file, "rw").use {
            it.seek(marker.toLong())
            it.write("TAMPERED".toByteArray(Charsets.US_ASCII))
        }
        PDDocument.load(file).use { pdf ->
            assertEquals(PdfSignatureIntegrity.INVALID, PdfCertificateCrypto.inspect(file, pdf).single().integrity)
        }
    }

    @Test
    fun trailingUnsignedBytesNeverCountAsCoverageOfTheWholeFile() {
        val keys = keyPair()
        val file = signedPdf(keys, certificate(keys))
        file.appendText("\n% unsigned later bytes\n", Charsets.US_ASCII)
        PDDocument.load(file).use { pdf ->
            val result = PdfCertificateCrypto.inspect(file, pdf).single()
            assertEquals(PdfSignatureIntegrity.VERIFIED, result.integrity)
            assertFalse(result.coversWholeDocument)
        }
    }

    @Test
    fun aDifferentPrivateKeyDoesNotPassVerification() {
        val certificateKeys = keyPair()
        val file = signedPdf(keyPair(), certificate(certificateKeys))
        PDDocument.load(file).use { pdf ->
            assertEquals(PdfSignatureIntegrity.INVALID, PdfCertificateCrypto.inspect(file, pdf).single().integrity)
        }
    }

    @Test
    fun ecCertificatesCanSignAndVerifyWithoutInstallingAGlobalProvider() {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val file = signedPdf(keys, certificate(keys))
        PDDocument.load(file).use { pdf ->
            assertEquals(PdfSignatureIntegrity.VERIFIED, PdfCertificateCrypto.inspect(file, pdf).single().integrity)
        }
    }

    @Test
    fun refusesExpiredOrEncryptionOnlyCertificatesBeforeSigning() {
        val keys = keyPair()
        val expired = certificate(keys, expired = true)
        assertThrows(CertificateExpiredException::class.java) {
            PdfCertificateCrypto.sign(ByteArrayInputStream(byteArrayOf(1, 2, 3)), keys.private, listOf(expired))
        }
        val encryptionOnly = certificate(keys, usage = KeyUsage.keyEncipherment)
        assertThrows(IllegalArgumentException::class.java) {
            PdfCertificateCrypto.sign(ByteArrayInputStream(byteArrayOf(1, 2, 3)), keys.private, listOf(encryptionOnly))
        }
    }

    @Test
    fun anUnsignedPdfProducesNoInventedSignature() {
        val file = original()
        PDDocument.load(file).use { assertTrue(PdfCertificateCrypto.inspect(file, it).isEmpty()) }
    }

    private fun original(): File = temporary.newFile().also { file ->
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.documentInformation.title = "ORIGINAL MARKER"
            document.save(file)
        }
    }

    private fun signedPdf(keys: KeyPair, certificate: X509Certificate): File {
        val input = original()
        val output = temporary.newFile()
        PDDocument.load(input).use { document ->
            val signature = PDSignature().apply {
                setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
            }
            SignatureOptions().use { options ->
                options.preferredSignatureSize = 16 * 1024
                document.addSignature(signature, { data ->
                    PdfCertificateCrypto.sign(data, keys.private, listOf(certificate))
                }, options)
                output.outputStream().use(document::saveIncremental)
            }
        }
        return output
    }

    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun certificate(
        keys: KeyPair,
        expired: Boolean = false,
        usage: Int = KeyUsage.digitalSignature,
    ): X509Certificate {
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=Teste Scaniqo")
        val builder = JcaX509v3CertificateBuilder(
            subject, BigInteger.valueOf(now),
            Date(now - 86_400_000L),
            Date(if (expired) now - 60_000L else now + 86_400_000L),
            subject, keys.public,
        ).addExtension(Extension.keyUsage, true, KeyUsage(usage))
        val algorithm = if (keys.private.algorithm == "EC") "SHA256withECDSA" else "SHA256withRSA"
        val holder = builder.build(JcaContentSignerBuilder(algorithm).setProvider(BouncyCastleProvider()).build(keys.private))
        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(holder)
    }
}
