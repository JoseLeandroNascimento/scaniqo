package com.joseleandro.scaniqo.data.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex

/** Uma única instância no aplicativo serializa backup manual, automático e restauração. */
class BackupOperationGate {
    val mutex = Mutex()
    val automaticRunning = MutableStateFlow(false)
}
