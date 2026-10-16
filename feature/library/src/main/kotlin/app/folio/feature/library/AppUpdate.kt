package app.folio.feature.library

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.folio.core.data.modules.InstallState
import app.folio.core.data.modules.ModuleApk
import app.folio.core.data.modules.ModuleInstaller
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Checks Folio's GitHub releases once per screen life and installs a newer one on request. */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(private val installer: ModuleInstaller) : ViewModel() {
    val update: StateFlow<ModuleApk?> = installer.appUpdate
    val states: StateFlow<Map<String, InstallState>> = installer.states

    init {
        viewModelScope.launch {
            try {
                installer.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AppUpdate", "Update check failed", e)
            }
        }
    }

    fun install(apk: ModuleApk) = installer.install(apk)
}

/** «Доступна версия X»: a card on Home while a newer Folio release exists; a tap downloads and installs it. */
@Composable
fun AppUpdateCard(modifier: Modifier = Modifier, vm: AppUpdateViewModel = hiltViewModel()) {
    val update by vm.update.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val apk = update ?: return
    val state = states[apk.packageName] ?: InstallState.Idle
    val c = Folio.colors
    val action = when (state) {
        is InstallState.Downloading -> state.fraction?.let { "${(it * 100).toInt()} %" } ?: stringResource(R.string.update_downloading)
        InstallState.Installing -> stringResource(R.string.update_installing)
        InstallState.NeedsPermission -> stringResource(R.string.update_permission)
        is InstallState.Failed -> stringResource(R.string.update_retry)
        InstallState.Idle -> stringResource(R.string.update_install)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .clickable(enabled = state !is InstallState.Downloading && state != InstallState.Installing) { vm.install(apk) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.update_title, apk.versionName), style = FolioType.headline, color = c.label)
            Text(stringResource(R.string.update_text), style = FolioType.footnote, color = c.secondaryLabel)
        }
        Text(action, style = FolioType.body, color = c.accent, modifier = Modifier.padding(start = 12.dp))
    }
}
