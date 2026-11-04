package com.joseleandro.scaniqo.core.security

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Pedido efêmero. O Cipher não é serializado e nunca contém uma senha de documento. */
class VaultAuthenticationRequest internal constructor(
    val id: String,
    internal val mode: VaultAuthenticationMode,
    val cipher: Cipher?,
    internal val allowsDeviceCredentialFallback: Boolean = false,
)

/**
 * A chave de dados fica somente na sessão. As versões de senha permanecem cifradas,
 * inclusive quando uma senha geral nova substitui o padrão usado nas próximas proteções.
 */
class PasswordVault(context: Context) : PasswordAccess {
    private val applicationContext = context.applicationContext
    private val storage = PasswordVaultStorage(applicationContext)
    private val keyStore = PasswordVaultKeyStore(applicationContext)
    private val session = PasswordSession(SystemClock::elapsedRealtime)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PasswordVaultState())
    override val state: StateFlow<PasswordVaultState> = mutableState.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = mutableReady.asStateFlow()

    @Volatile private var envelope: VaultEnvelope? = null
    @Volatile private var foreground = false
    private val pendingMonitor = Any()
    private var pending: PendingAuthentication? = null
    private val mutableAuthenticationRequest = MutableStateFlow<VaultAuthenticationRequest?>(null)
    val authenticationRequest: StateFlow<VaultAuthenticationRequest?> =
        mutableAuthenticationRequest.asStateFlow()

    override val activeKeyId: String?
        get() = envelope?.activeKeyId

    val hasVault: Boolean
        get() = envelope != null || state.value.recoveryRequired

    val canEnableLegacyBiometrics: Boolean
        get() = envelope?.primary?.mode == VaultAuthenticationMode.DEVICE_CREDENTIAL &&
            envelope?.biometric == null &&
            BiometricManager.from(applicationContext).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) == BiometricManager.BIOMETRIC_SUCCESS

    private val loaded = scope.async(Dispatchers.IO) {
        try {
            envelope = storage.read()
            publishState()
        } catch (_: Exception) {
            markRecoveryRequired()
        } finally {
            mutableReady.value = true
        }
    }

    init {
        scope.launch {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    foreground = true
                }

                override fun onStop(owner: LifecycleOwner) {
                    foreground = false
                    // O pedido de autenticação fica vivo: o Keyguard também coloca o app em segundo plano.
                    lock()
                }
            })
            while (true) {
                delay(1_000)
                publishState()
            }
        }
    }

    suspend fun beginSetup(
        password: String,
        replaceUnavailableVault: Boolean = false,
    ): VaultAuthenticationRequest {
        require(PasswordPolicy.isValidGeneral(password))
        loaded.await()
        return mutex.withLock {
            ensureNoPending()
            check(!hasVault || (state.value.recoveryRequired && replaceUnavailableVault))
            withContext(Dispatchers.IO) {
                val id = UUID.randomUUID().toString()
                val versionId = UUID.randomUUID().toString()
                val dataKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val alias = "scaniqo.password." + UUID.randomUUID()
                val mode = if (Build.VERSION.SDK_INT >= 30) {
                    VaultAuthenticationMode.COMBINED
                } else {
                    VaultAuthenticationMode.DEVICE_CREDENTIAL
                }
                try {
                    keyStore.generate(alias, mode)
                    val payload = VaultPayload(
                        versionId,
                        linkedMapOf(versionId to password.toByteArray(Charsets.UTF_8)),
                    ).use { encryptPayload(it, dataKey, id) }
                    val cipher = if (mode == VaultAuthenticationMode.DEVICE_CREDENTIAL) null
                    else keyStore.cipher(alias)
                    registerPending(
                        PendingAuthentication(
                            request = VaultAuthenticationRequest(UUID.randomUUID().toString(), mode, cipher),
                            operation = Operation.SETUP,
                            vaultId = id,
                            alias = alias,
                            dataKey = dataKey,
                            newPayload = payload,
                            activeId = versionId,
                            replaceUnavailable = replaceUnavailableVault,
                        ),
                    )
                } catch (error: Exception) {
                    dataKey.fill(0)
                    runCatching { keyStore.removeUnused(alias) }
                    throw error
                }
            }
        }
    }

    suspend fun beginUnlock(useDeviceCredential: Boolean = false): VaultAuthenticationRequest {
        loaded.await()
        return mutex.withLock {
            ensureNoPending()
            if (state.value.recoveryRequired) throw PasswordVaultRecoveryException()
            val current = envelope ?: throw PasswordVersionMissingException()
            withContext(Dispatchers.IO) {
                var wrapper = current.biometric?.takeUnless { useDeviceCredential } ?: current.primary
                val cipher = try {
                    unlockCipher(wrapper)
                } catch (error: Exception) {
                    if (wrapper == current.biometric) {
                        // Uma nova biometria pode invalidar somente o envelope opcional.
                        wrapper = current.primary
                        unlockCipher(wrapper)
                    } else {
                        if (isPermanentFailure(error)) markRecoveryRequired()
                        throw error
                    }
                }
                registerPending(
                    PendingAuthentication(
                        request = VaultAuthenticationRequest(
                            UUID.randomUUID().toString(), wrapper.mode, cipher,
                            allowsDeviceCredentialFallback = wrapper.mode == VaultAuthenticationMode.BIOMETRIC,
                        ),
                        operation = Operation.UNLOCK,
                        vaultId = current.id,
                        alias = wrapper.alias,
                        wrappedKey = wrapper.ciphertext,
                    ),
                )
            }
        }
    }

    suspend fun beginEnableLegacyBiometrics(): VaultAuthenticationRequest {
        loaded.await()
        return mutex.withLock {
            ensureNoPending()
            check(canEnableLegacyBiometrics)
            val current = checkNotNull(envelope)
            withContext(Dispatchers.IO) {
                val dataKey = session.copyKey()
                val alias = "scaniqo.password." + UUID.randomUUID()
                try {
                    keyStore.generate(alias, VaultAuthenticationMode.BIOMETRIC)
                    registerPending(
                        PendingAuthentication(
                            request = VaultAuthenticationRequest(
                                UUID.randomUUID().toString(),
                                VaultAuthenticationMode.BIOMETRIC,
                                keyStore.cipher(alias),
                            ),
                            operation = Operation.ENABLE_BIOMETRICS,
                            vaultId = current.id,
                            alias = alias,
                            dataKey = dataKey,
                        ),
                    )
                } catch (error: Exception) {
                    dataKey.fill(0)
                    runCatching { keyStore.removeUnused(alias) }
                    throw error
                }
            }
        }
    }

    suspend fun completeAuthentication(requestId: String, authenticatedCipher: Cipher?) {
        loaded.await()
        mutex.withLock {
            val operation = synchronized(pendingMonitor) {
                pending?.takeIf { it.request.id == requestId }?.also {
                    pending = null
                    mutableAuthenticationRequest.value = null
                }
            } ?: throw PasswordVaultLockedException()
            var persistedNewKey = false
            try {
                withContext(Dispatchers.IO) {
                    val cipher = if (operation.request.mode == VaultAuthenticationMode.DEVICE_CREDENTIAL) {
                        keyStore.cipher(
                            operation.alias,
                            operation.wrappedKey,
                        )
                    } else {
                        check(authenticatedCipher === operation.request.cipher)
                        requireNotNull(authenticatedCipher)
                    }

                    if (operation.operation == Operation.UNLOCK) {
                        val key = PasswordVaultKeyCipher.decryptAuthenticated(
                            cipher,
                            checkNotNull(operation.wrappedKey),
                            operation.vaultId,
                            operation.request.mode,
                        )
                        try {
                            check(key.size == 32)
                            readPayload(checkNotNull(envelope), key).use { }
                            if (foreground) session.unlock(key)
                        } finally {
                            key.fill(0)
                        }
                    } else {
                        val key = checkNotNull(operation.dataKey)
                        val wrapped = VaultKeyEnvelope(
                            operation.alias,
                            operation.request.mode,
                            PasswordVaultKeyCipher.encryptAuthenticated(
                                cipher,
                                key,
                                operation.vaultId,
                                operation.request.mode,
                            ),
                        )
                        val updated = if (operation.operation == Operation.SETUP) {
                            VaultEnvelope(
                                operation.vaultId,
                                operation.activeId,
                                wrapped,
                                null,
                                checkNotNull(operation.newPayload),
                            )
                        } else {
                            checkNotNull(envelope).copy(biometric = wrapped)
                        }
                        withContext(NonCancellable) {
                            if (operation.replaceUnavailable) storage.preserveRecoveryCopy()
                            storage.write(updated)
                            envelope = updated
                            persistedNewKey = true
                        }
                        mutableState.update { it.copy(recoveryRequired = false) }
                        if (foreground) session.unlock(key)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (operation.operation == Operation.UNLOCK &&
                    operation.request.mode != VaultAuthenticationMode.BIOMETRIC &&
                    isPermanentFailure(error)
                ) {
                    markRecoveryRequired()
                }
                if (error is UserNotAuthenticatedException) throw PasswordVaultLockedException()
                throw error
            } finally {
                operation.dataKey?.fill(0)
                if (operation.operation != Operation.UNLOCK && !persistedNewKey) {
                    // Uma tentativa sem gravação não deixa chaves órfãs; versões persistidas são preservadas.
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { keyStore.removeUnused(operation.alias) }
                    }
                }
                publishState()
            }
        }
    }

    fun cancelAuthentication(requestId: String) {
        val discarded = synchronized(pendingMonitor) {
            pending?.takeIf { it.request.id == requestId }?.also {
                pending = null
                mutableAuthenticationRequest.value = null
            }
        } ?: return
        discarded.dataKey?.fill(0)
        if (discarded.operation != Operation.UNLOCK) {
            scope.launch(Dispatchers.IO) { runCatching { keyStore.removeUnused(discarded.alias) } }
        }
    }

    suspend fun setGeneralPassword(password: String): String {
        require(PasswordPolicy.isValidGeneral(password))
        return addVersion(password, makeActive = true)
    }

    suspend fun rememberDocumentPassword(password: String): String {
        require(PasswordPolicy.isValidDocument(password))
        loaded.await()
        check(activeKeyId != null) { "Configure uma senha geral primeiro." }
        return addVersion(password, makeActive = false)
    }

    suspend fun deactivateGeneralPassword() {
        mutatePayload { it.activeKeyId = null }
    }

    override suspend fun <T> withPassword(keyId: String?, block: suspend (String) -> T): T {
        loaded.await()
        var passwordBytes: ByteArray? = null
        return try {
            mutex.withLock {
                if (state.value.recoveryRequired) throw PasswordVaultRecoveryException()
                val current = envelope ?: throw PasswordVersionMissingException()
                val key = session.copyKey()
                try {
                    withContext(Dispatchers.IO) {
                        readPayload(current, key).use { payload ->
                            val id = keyId ?: payload.activeKeyId
                            // A atribuição externa permite apagar o buffer mesmo se o retorno de IO for cancelado.
                            passwordBytes = payload.versions[id]?.copyOf()
                                ?: throw PasswordVersionMissingException()
                        }
                    }
                } finally {
                    key.fill(0)
                    publishState()
                }
            }
            // A biblioteca de PDF exige String. Esta referência vive apenas durante a operação.
            block(checkNotNull(passwordBytes).toString(Charsets.UTF_8))
        } finally {
            passwordBytes?.fill(0)
        }
    }

    override fun lock() {
        session.lock()
        publishState()
    }

    private suspend fun addVersion(password: String, makeActive: Boolean): String {
        val id = UUID.randomUUID().toString()
        mutatePayload { payload ->
            payload.versions[id] = password.toByteArray(Charsets.UTF_8)
            if (makeActive) payload.activeKeyId = id
        }
        return id
    }

    private suspend fun mutatePayload(change: (VaultPayload) -> Unit) {
        loaded.await()
        mutex.withLock {
            ensureNoPending()
            if (state.value.recoveryRequired) throw PasswordVaultRecoveryException()
            val current = envelope ?: throw PasswordVersionMissingException()
            val key = session.copyKey()
            try {
                withContext(Dispatchers.IO) {
                    readPayload(current, key).use { payload ->
                        change(payload)
                        val updated = current.copy(
                            activeKeyId = payload.activeKeyId,
                            payload = encryptPayload(payload, key, current.id),
                        )
                        // Gravação e troca do estado são indivisíveis do ponto de vista do cancelamento.
                        withContext(NonCancellable) {
                            storage.write(updated)
                            envelope = updated
                        }
                    }
                }
            } finally {
                key.fill(0)
                publishState()
            }
        }
    }

    private fun readPayload(current: VaultEnvelope, key: ByteArray): VaultPayload {
        val plaintext = PasswordVaultCrypto.decrypt(key, current.payload, current.id)
        return try {
            PasswordVaultFormat.decodePayload(plaintext).also { payload ->
                if (payload.activeKeyId != current.activeKeyId) {
                    payload.close()
                    throw PasswordVaultRecoveryException()
                }
            }
        } finally {
            plaintext.fill(0)
        }
    }

    private fun encryptPayload(payload: VaultPayload, key: ByteArray, id: String): VaultCiphertext {
        val plaintext = PasswordVaultFormat.encodePayload(payload)
        return try {
            PasswordVaultCrypto.encrypt(key, plaintext, id)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun unlockCipher(wrapper: VaultKeyEnvelope): Cipher? =
        if (wrapper.mode == VaultAuthenticationMode.DEVICE_CREDENTIAL) {
            keyStore.requireSecureDevice()
            null
        } else {
            keyStore.cipher(wrapper.alias, wrapper.ciphertext)
        }

    private fun registerPending(value: PendingAuthentication): VaultAuthenticationRequest {
        synchronized(pendingMonitor) {
            check(pending == null)
            pending = value
            mutableAuthenticationRequest.value = value.request
        }
        scope.launch {
            delay(2 * 60 * 1_000L)
            cancelAuthentication(value.request.id)
        }
        return value.request
    }

    private fun ensureNoPending() {
        synchronized(pendingMonitor) { check(pending == null) { "Autenticação em andamento." } }
    }

    private fun isPermanentFailure(error: Exception): Boolean =
        error is KeyPermanentlyInvalidatedException || error is UnrecoverableKeyException ||
            error is PasswordVaultRecoveryException || error is AEADBadTagException

    private fun publishState() {
        mutableState.update {
            it.copy(isConfigured = activeKeyId != null, isUnlocked = session.isUnlocked())
        }
    }

    private fun markRecoveryRequired() {
        session.lock()
        mutableState.value = PasswordVaultState(
            isConfigured = activeKeyId != null,
            recoveryRequired = true,
        )
    }

    private enum class Operation { SETUP, UNLOCK, ENABLE_BIOMETRICS }

    private class PendingAuthentication(
        val request: VaultAuthenticationRequest,
        val operation: Operation,
        val vaultId: String,
        val alias: String,
        val dataKey: ByteArray? = null,
        val wrappedKey: VaultCiphertext? = null,
        val newPayload: VaultCiphertext? = null,
        val activeId: String? = null,
        val replaceUnavailable: Boolean = false,
    )
}
