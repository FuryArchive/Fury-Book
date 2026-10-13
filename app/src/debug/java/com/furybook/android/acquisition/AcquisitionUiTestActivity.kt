package com.furybook.android.acquisition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.furybook.android.ui.DublAppContent
import com.furybook.ui.theme.DublTheme
import kotlinx.coroutines.*
import java.util.UUID

/** Non-exported debug-only host. Production MainActivity never reads these fixture extras. */
class AcquisitionUiTestActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var fixture: AndroidAcquisitionUiFixture? by mutableStateOf(null); private set
    var loadError: String? by mutableStateOf(null); private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val run = savedInstanceState?.getString("run") ?: intent.getStringExtra("fixtureRun") ?: UUID.randomUUID().toString()
        val resume = savedInstanceState != null || intent.getBooleanExtra("resumeFixture", false)
        scope.launch {
            try { fixture = AndroidAcquisitionUiFixture.open(this@AcquisitionUiTestActivity, run, resume) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { loadError = failure.message ?: "Fixture initialization failed" }
        }
        setContent { DublTheme {
            val value = fixture
            if (value == null) Text(loadError ?: "Подготовка SYNTHETIC_RULE")
            else DublAppContent(value.controller, value.content)
        } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        fixture?.let { outState.putString("run", it.runId) }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { fixture?.controller?.dispose(); scope.cancel(); super.onDestroy() }
}
