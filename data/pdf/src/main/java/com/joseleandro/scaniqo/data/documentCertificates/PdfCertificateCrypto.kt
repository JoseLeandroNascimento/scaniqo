package com.joseleandro.scaniqo.data.documentCertificates

import com.joseleandro.scaniqo.domain.model.PdfCertificateSignature
import com.joseleandro.scaniqo.domain.model.PdfSignatureIntegrity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.CMSTypedData
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder

/** CMS integrity only: this deliberately does not claim CA trust, revocation or trusted time. */
internal object PdfCertificateCrypto {
    private val provider = BouncyCastleProvider()

    fun sign(content: InputStream, key: PrivateKey, chain: List<X509Certificate>): ByteArray {
        require(chain.isNotEmpty()) { "O certificado selecionado não possui uma cadeia disponível." }
        val certificate = chain.first()
        certificate.checkValidity()
        certificate.keyUsage?.let { usage ->
            require(usage.getOrElse(0) { false } || usage.getOrElse(1) { false }) {
                "Este certificado não permite assinatura digital."
            }
        }
        val algorithm = when (key.algorithm.uppercase()) {
            "RSA" -> "SHA256withRSA"
            "EC", "ECDSA" -> "SHA256withECDSA"
            else -> error("Selecione um certificado RSA ou EC.")
        }
        // Leave provider selection to Android so non-exportable KeyChain keys remain usable.
        val signature = Signature.getInstance(algorithm).apply { initSign(key) }
        val signer = object : ContentSigner {
            override fun getAlgorithmIdentifier() = DefaultSignatureAlgorithmIdentifierFinder().find(algorithm)
            override fun getOutputStream(): OutputStream = object : OutputStream() {
                override fun write(value: Int) { signature.update(value.toByte()) }
                override fun write(bytes: ByteArray, offset: Int, length: Int) { signature.update(bytes, offset, length) }
            }
            override fun getSignature(): ByteArray = signature.sign()
        }
        val generator = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(
                JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().setProvider(provider).build())
                    .build(signer, certificate)
            )
            addCertificates(JcaCertStore(chain))
        }
        val data = object : CMSTypedData {
            override fun getContentType() = CMSObjectIdentifiers.data
            override fun getContent(): Any = content
            override fun write(output: OutputStream) { content.copyTo(output) }
        }
        return generator.generate(data, false).encoded
    }

    fun inspect(file: File, document: PDDocument): List<PdfCertificateSignature> {
        require(document.signatureDictionaries.size <= 32) { "Este PDF possui assinaturas demais para verificar no aparelho." }
        return document.signatureDictionaries.map { signature -> inspect(file, signature) }
    }

    private fun inspect(file: File, signature: PDSignature): PdfCertificateSignature {
        val unknown = PdfCertificateSignature(
            signer = "Signatário não identificado",
            integrity = PdfSignatureIntegrity.INVALID,
            coversWholeDocument = false,
            certificateCurrent = false,
        )
        val range = PdfSignatureByteRange.parse(signature.byteRange, file.length())
            ?: return unknown.copy(detail = "A assinatura contém intervalos de bytes inválidos.")
        if (signature.subFilter !in setOf("adbe.pkcs7.detached", "ETSI.CAdES.detached")) {
            return unknown.copy(integrity = PdfSignatureIntegrity.UNSUPPORTED,
                detail = "Este formato de assinatura não é compatível com a verificação local.")
        }
        return try {
            require(range.secondOffset - range.firstLength <= 1024 * 1024)
            RandomAccessFile(file, "r").use { input ->
                input.seek(range.firstLength.toLong())
                require(input.read() == '<'.code)
                input.seek(range.secondOffset.toLong() - 1)
                require(input.read() == '>'.code)
            }
            val contents = file.inputStream().use(signature::getContents)
            require(contents.contentEquals(signature.contents)) { "Conteúdo da assinatura inconsistente." }
            val envelope = CMSSignedData(contents)
            val signers = envelope.signerInfos.signers.toList()
            require(signers.isNotEmpty())
            if (signers.any { it.digestAlgOID !in setOf(
                "2.16.840.1.101.3.4.2.1", // SHA-256
                "2.16.840.1.101.3.4.2.2", // SHA-384
                "2.16.840.1.101.3.4.2.3", // SHA-512
            ) }) return unknown.copy(
                integrity = PdfSignatureIntegrity.UNSUPPORTED,
                detail = "A verificação local aceita assinaturas com SHA-256, SHA-384 ou SHA-512.",
            )
            val hashes = signers.map { it.digestAlgOID }.distinct().associateWith { oid ->
                digestRange(file, range, oid)
            }
            val cms = CMSSignedData(hashes, contents)
            var allCurrent = true
            val names = mutableListOf<String>()
            val integrity = cms.signerInfos.signers.all { signer ->
                val holder = cms.certificates.getMatches(null).singleOrNull { signer.sid.match(it) }
                    ?: return@all false
                val cert = JcaX509CertificateConverter().setProvider(provider).getCertificate(holder)
                names += cert.subjectX500Principal.name
                allCurrent = allCurrent && runCatching { cert.checkValidity() }.isSuccess
                signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(provider).build(holder))
            }
            unknown.copy(
                signer = names.joinToString("; ").ifBlank { unknown.signer },
                integrity = if (integrity) PdfSignatureIntegrity.VERIFIED else PdfSignatureIntegrity.INVALID,
                coversWholeDocument = range.covers(file.length()),
                certificateCurrent = allCurrent && names.isNotEmpty(),
                detail = if (range.covers(file.length())) null else "Há bytes posteriores à revisão assinada.",
            )
        } catch (_: Exception) {
            unknown.copy(detail = "Não foi possível confirmar a integridade desta assinatura.")
        }
    }

    private fun digestRange(file: File, range: PdfSignatureByteRange, oid: String): ByteArray {
        val digest = MessageDigest.getInstance(oid, provider)
        RandomAccessFile(file, "r").use { input ->
            val buffer = ByteArray(32 * 1024)
            for ((offset, count) in listOf(0L to range.firstLength, range.secondOffset.toLong() to range.secondLength)) {
                input.seek(offset)
                var remaining = count
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(remaining, buffer.size))
                    require(read > 0)
                    digest.update(buffer, 0, read)
                    remaining -= read
                }
            }
        }
        return digest.digest()
    }
}
