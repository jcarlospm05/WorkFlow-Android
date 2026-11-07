package com.workflow.operaciones

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.app.KeyguardManager
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.workflow.operaciones.ui.*

class MainActivity : FragmentActivity() {
    private var unlocked: Boolean
        get() = PhoneSession.unlocked
        set(value) { PhoneSession.unlocked = value }
    private var promptActive = false
    private var credentialGeneration = 0
    private var attempted = false
    private var unlockError by mutableStateOf<String?>(null)
    private var inbound by mutableStateOf<Uri?>(null)
    private var alarmDay by mutableStateOf<String?>(null)
    private lateinit var model: WorkflowViewModel
    private val credentialLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        promptActive = false
        if (credentialGeneration != PhoneSession.lockGeneration) {
            attempted = false; unlockError = null; window.decorView.post { unlock() }
        } else if (result.resultCode == RESULT_OK && !getSystemService(KeyguardManager::class.java).isDeviceLocked) unlocked = true
        else unlockError = "Desbloqueo cancelado"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkflowViewModel((application as WorkflowApp).repository) as T
        })[WorkflowViewModel::class.java]
        capture(intent)
        setContent {
            WorkflowTheme {
                val screenState = rememberSaveableStateHolder()
                if (unlocked) {
                    LaunchedEffect(alarmDay,model.busy){if(!model.busy)alarmDay?.let{model.openAgendaDay(it);alarmDay=null}}
                    LaunchedEffect(inbound, model.busy) { if (!model.busy) inbound?.let { model.preview(it); inbound = null } }
                    screenState.SaveableStateProvider("workflow") { WorkflowScreen(model) }
                } else Surface(color = Brand.Background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        BrandHeader()
                        Spacer(Modifier.height(24.dp))
                        Text("Tu información está protegida", style = MaterialTheme.typography.titleLarge)
                        Text("Desbloquea con la seguridad de Android. El teléfono debe tener PIN, patrón, contraseña o biometría.")
                        LaunchedEffect(unlocked, PhoneSession.presentEvents, PhoneSession.lockGeneration) { if (!unlocked && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) unlock() }
                        unlockError?.let { Text(it, color = Brand.Red); TextButton(onClick = { attempted = false; unlock() }) { Text("Intentar de nuevo") } }
                        TextButton(onClick = { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) { Text("Configurar bloqueo del teléfono") }
                    }
                }
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun unlock() {
        if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) || unlocked || promptActive || attempted || getSystemService(KeyguardManager::class.java).isDeviceLocked) return
        attempted = true; unlockError = null
        val keyguard = getSystemService(KeyguardManager::class.java)
        val generation = PhoneSession.lockGeneration
        fun credential() {
            val intent = keyguard.createConfirmDeviceCredentialIntent("WorkFlow", "Desbloquear datos del supervisor")
            if (intent != null) { credentialGeneration = PhoneSession.lockGeneration; promptActive = true; credentialLauncher.launch(intent) }
            else unlockError = "Configura un PIN, patrón, contraseña o biometría en el teléfono"
        }
        val authenticators = if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL else BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            credential(); return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                promptActive = false
                if (generation == PhoneSession.lockGeneration && !keyguard.isDeviceLocked) unlocked = true
                else { attempted = false; window.decorView.post { unlock() } }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                promptActive = false
                if (generation != PhoneSession.lockGeneration) { attempted = false; unlockError = null; window.decorView.post { unlock() }; return }
                if (Build.VERSION.SDK_INT < 30 && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) credential()
                else unlockError = "No se completó el desbloqueo. Puedes intentarlo de nuevo."
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Desbloquear WorkFlow").setAllowedAuthenticators(authenticators)
        if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("Usar código del teléfono")
        promptActive = true; prompt.authenticate(info.build())
    }
    override fun onResume() {
        super.onResume()
        (application as WorkflowApp).agendaScheduler.requestReconcile()
        if (getSystemService(KeyguardManager::class.java).isDeviceLocked) unlocked = false
        window.decorView.post { if (!unlocked) unlock() }
    }
    override fun onStop() { super.onStop(); if (!promptActive) attempted = false }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); capture(intent) }
    @Suppress("DEPRECATION")
    private fun capture(intent: Intent?) {
        if(intent?.action==com.workflow.operaciones.reminders.AgendaScheduler.OPEN) {
            intent.getStringExtra("day")?.let { value -> runCatching { com.workflow.core.Rules.day(value) }.onSuccess { alarmDay=value } }
        }
        val uri = when (intent?.action) { Intent.ACTION_VIEW -> intent.data; Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM); else -> null }
        if (uri?.scheme == "content") inbound = uri
    }
}
