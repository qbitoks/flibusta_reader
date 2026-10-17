package app.folio.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.folio.core.data.modules.InstallState
import app.folio.core.data.modules.ModuleApk
import app.folio.core.data.modules.ModuleInstaller
import app.folio.core.data.modules.ModulesRepository
import app.folio.core.data.settings.Appearance
import app.folio.core.data.settings.SettingsRepository
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.GroupHeader
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.GroupedRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the manual check for a newer Folio stands. */
enum class UpdateCheck { Checking, Done, Failed }

/** «Настройки»: the app appearance and Folio's own update from GitHub, checked when the screen opens. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val installer: ModuleInstaller,
) : ViewModel() {
    val appearance: StateFlow<Appearance> =
        settings.appAppearance.stateIn(viewModelScope, SharingStarted.Eagerly, Appearance.Auto)
    val update: StateFlow<ModuleApk?> = installer.appUpdate
    val states: StateFlow<Map<String, InstallState>> = installer.states

    private val _check = MutableStateFlow(UpdateCheck.Checking)
    val check: StateFlow<UpdateCheck> = _check.asStateFlow()
    private var checking: Job? = null

    init {
        checkUpdate()
    }

    fun setAppearance(value: Appearance) = viewModelScope.launch { settings.setAppAppearance(value) }

    fun checkUpdate() {
        if (checking?.isActive == true) return
        checking = viewModelScope.launch {
            _check.value = UpdateCheck.Checking
            _check.value = if (installer.checkApp()) UpdateCheck.Done else UpdateCheck.Failed
        }
    }

    fun install(apk: ModuleApk) = installer.install(apk)
}

/**
 * «Настройки», opened by the gear on «Главная»: appearance, reading, «Обновление», «О приложении».
 * Sheets of other features open through host callbacks ([onReadingSettings]).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onReadingSettings: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val c = Folio.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.groupedBackground)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 32.dp),
    ) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            CircleButton(Icons.AutoMirrored.Rounded.ArrowBackIos, stringResource(R.string.settings_back), onBack, modifier = Modifier.align(Alignment.CenterStart))
        }
        Text(stringResource(R.string.settings_title), style = FolioType.largeTitle, color = c.label, modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp))
        AppearanceGroup(vm)
        GroupHeader(stringResource(R.string.settings_reading_header))
        GroupedCard {
            GroupedRow(stringResource(R.string.settings_reading), icon = Icons.Rounded.TextFields, chevron = true, showDivider = false, onClick = onReadingSettings)
        }
        UpdateGroup(vm)
        AboutGroup()
    }
}

@Composable
private fun AppearanceGroup(vm: SettingsViewModel) {
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val options = listOf(
        Triple(Appearance.Auto, R.string.settings_appearance_auto, Icons.Rounded.BrightnessAuto),
        Triple(Appearance.Light, R.string.settings_appearance_light, Icons.Rounded.LightMode),
        Triple(Appearance.Dark, R.string.settings_appearance_dark, Icons.Rounded.DarkMode),
    )
    GroupHeader(stringResource(R.string.settings_appearance))
    GroupedCard {
        options.forEachIndexed { i, (value, label, icon) ->
            GroupedRow(
                stringResource(label),
                icon = icon,
                showDivider = i < options.lastIndex,
                trailing = { if (appearance == value) Icon(Icons.Rounded.Check, null, tint = Folio.colors.accent, modifier = Modifier.padding(start = 8.dp).size(22.dp)) },
                onClick = { vm.setAppearance(value) },
            )
        }
    }
}

@Composable
private fun UpdateGroup(vm: SettingsViewModel) {
    val c = Folio.colors
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val update by vm.update.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    GroupHeader(stringResource(R.string.settings_update))
    GroupedCard {
        GroupedRow(stringResource(R.string.settings_version), value = version)
        val apk = update
        if (apk != null) {
            // A newer release: the same download/install flow as the card on «Главная».
            val state = states[apk.packageName] ?: InstallState.Idle
            GroupedRow(
                stringResource(R.string.settings_install_title, apk.versionName),
                subtitle = stringResource(R.string.settings_install_text),
                showDivider = false,
                trailing = { Text(installAction(state), style = FolioType.body, color = c.accent, modifier = Modifier.padding(start = 12.dp)) },
                onClick = { if (state !is InstallState.Downloading && state != InstallState.Installing) vm.install(apk) },
            )
        } else {
            GroupedRow(
                stringResource(R.string.settings_check_update),
                subtitle = when (check) {
                    UpdateCheck.Checking -> stringResource(R.string.settings_checking)
                    UpdateCheck.Done -> stringResource(R.string.settings_up_to_date)
                    UpdateCheck.Failed -> stringResource(R.string.settings_check_failed)
                },
                titleColor = c.accent,
                showDivider = false,
                trailing = {
                    if (check == UpdateCheck.Checking) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = c.secondaryLabel, trackColor = c.fill, strokeWidth = 2.dp)
                    }
                },
                onClick = vm::checkUpdate,
            )
        }
    }
}

@Composable
private fun AboutGroup() {
    val c = Folio.colors
    val uri = LocalUriHandler.current
    var licenses by rememberSaveable { mutableStateOf(false) }
    GroupHeader(stringResource(R.string.settings_about))
    GroupedCard {
        GroupedRow(
            stringResource(R.string.settings_project_page),
            value = "GitHub",
            trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = c.tertiaryLabel, modifier = Modifier.padding(start = 8.dp).size(20.dp)) },
            onClick = { runCatching { uri.openUri(ModulesRepository.APP_PAGE) } },
        )
        GroupedRow(stringResource(R.string.settings_licenses), chevron = true, showDivider = false, onClick = { licenses = true })
    }
    if (licenses) LicensesSheet(onDismiss = { licenses = false })
    Text(
        stringResource(R.string.settings_fonts_text),
        style = FolioType.footnote,
        color = c.secondaryLabel,
        modifier = Modifier.padding(start = 34.dp, end = 34.dp, top = 8.dp),
    )
}

/** The update's action label for [state]: «Обновить», progress, «Разрешите установку» or «Повторить». */
@Composable
private fun installAction(state: InstallState): String = when (state) {
    is InstallState.Downloading -> state.fraction?.let { "${(it * 100).toInt()} %" } ?: stringResource(R.string.settings_install_downloading)
    InstallState.Installing -> stringResource(R.string.settings_install_installing)
    InstallState.NeedsPermission -> stringResource(R.string.settings_install_permission)
    is InstallState.Failed -> stringResource(R.string.settings_install_retry)
    InstallState.Idle -> stringResource(R.string.settings_install_install)
}
