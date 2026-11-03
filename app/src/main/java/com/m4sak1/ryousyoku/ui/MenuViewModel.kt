package com.m4sak1.ryousyoku.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m4sak1.ryousyoku.model.*
import com.m4sak1.ryousyoku.model.Menu
import com.m4sak1.ryousyoku.model.BreakfastDefault
import com.m4sak1.ryousyoku.repository.MenuRepository
import com.m4sak1.ryousyoku.model.StructuredMenu
import com.m4sak1.ryousyoku.model.selectionKey
import com.m4sak1.ryousyoku.model.ArchiveSnapshot
import com.m4sak1.ryousyoku.model.makeNutritionExport
import com.m4sak1.ryousyoku.model.nutritionExportJson
import com.m4sak1.ryousyoku.model.nutritionExportText
import com.m4sak1.ryousyoku.repository.LoadedStructuredMenu
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class MenuState {
    object Loading : MenuState()
    data class Success(
        val allMenus: List<Menu>,
        val activeMenu: Menu?
    ) : MenuState()
    data class Error(val message: String) : MenuState()
}

sealed class StructuredState {
    object Loading : StructuredState()
    data class Ready(val data: StructuredMenu, val fromCache: Boolean) : StructuredState()
    object Unavailable : StructuredState()
}
sealed class ArchiveState {
    object Idle : ArchiveState()
    object Loading : ArchiveState()
    data class Ready(val snapshot: ArchiveSnapshot) : ArchiveState()
}

class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MenuRepository(application.cacheDir)
    private val preferences = application.getSharedPreferences("meal_selections", Context.MODE_PRIVATE)
    private val appPreferences = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val _breakfastDefault = MutableStateFlow(runCatching {
        BreakfastDefault.valueOf(appPreferences.getString("breakfast_default", null) ?: "NONE")
    }.getOrDefault(BreakfastDefault.NONE))
    val breakfastDefault = _breakfastDefault.asStateFlow()

    fun setBreakfastDefault(value: BreakfastDefault) {
        appPreferences.edit().putString("breakfast_default", value.name).apply()
        _breakfastDefault.value = value
        com.m4sak1.ryousyoku.widget.NextMealWidgetController.refresh(getApplication())
    }
    private val _preferredDate = MutableStateFlow<String?>(null)
    val preferredDate = _preferredDate.asStateFlow()
    fun openWidgetDate(value: String) {
        val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return
        val menus = (_uiState.value as? MenuState.Success)?.allMenus ?: return
        val target = menus.filter { menuCoversDate(it, date) }.singleOrNull() ?: return
        _preferredDate.value = date.toString()
        selectMenu(target)
    }
    private val _selectedMenu = MutableStateFlow<Menu?>(null)
    val selectedMenu = _selectedMenu.asStateFlow()
    private val _structured = MutableStateFlow<StructuredState>(StructuredState.Loading)
    val structured = _structured.asStateFlow()
    private val _selections = MutableStateFlow(preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.let { key to it }
    }.toMap())
    val selections = _selections.asStateFlow()
    private var structuredJob: Job? = null
    private var archiveJob: Job? = null
    private val _archive = MutableStateFlow<ArchiveState>(ArchiveState.Idle)
    val archive = _archive.asStateFlow()
    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage = _exportMessage.asStateFlow()
    private var pendingExport: String? = null

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage = _backupMessage.asStateFlow()
    private val _backupPreview = MutableStateFlow<PersonalBackup?>(null)
    val backupPreview = _backupPreview.asStateFlow()
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy = _backupBusy.asStateFlow()
    private var pendingBackup: String? = null

    fun prepareBackup(): String? = try {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"))
        pendingBackup = encodeBackup(PersonalBackup(createdAt = now.toOffsetDateTime().toString(),
            breakfastDefault = _breakfastDefault.value, selections = _selections.value.toSortedMap().toMap()))
        _backupMessage.value = null
        "ryousyoku-backup-${now.toLocalDate()}.json"
    } catch (_: Exception) {
        _backupMessage.value = "記録を確認できないためバックアップできません。"
        null
    }

    fun saveBackup(uri: Uri?) {
        val body = pendingBackup
        pendingBackup = null
        if (uri == null || body == null || _backupBusy.value) return
        _backupBusy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw java.io.IOException()
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                }
                _backupMessage.value = "バックアップを保存しました。"
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { _backupMessage.value = "保存できませんでした。保存先を確認してください。"
            } finally { _backupBusy.value = false }
        }
    }

    fun readBackup(uri: Uri?) {
        if (uri == null || _backupBusy.value) return
        _backupPreview.value = null
        _backupMessage.value = null
        _backupBusy.value = true
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException()
                    val bytes = stream.use { it.readBytesLimited(MAX_BACKUP_BYTES) }
                    decodeBackup(bytes.toString(Charsets.UTF_8))
                }
                _backupPreview.value = data
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                _backupMessage.value = "読み込めませんでした。対応するバックアップJSONか確認してください。記録は変更していません。"
            } finally { _backupBusy.value = false }
        }
    }

    fun dismissBackupPreview() { if (!_backupBusy.value) _backupPreview.value = null }

    fun restoreBackup(replace: Boolean) {
        val backup = _backupPreview.value ?: return
        if (_backupBusy.value) return
        val previous = _selections.value
        val oldBreakfast = _breakfastDefault.value
        val updated = restoredSelections(previous, backup, replace)
        _backupBusy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    fun writeRecords(records: Map<String, String>) = preferences.edit().clear().also { editor ->
                        records.forEach { (key, value) -> editor.putString(key, value) }
                    }.commit()
                    if (!writeRecords(updated) || !appPreferences.edit().putString("breakfast_default", backup.breakfastDefault.name).commit()) {
                        writeRecords(previous)
                        appPreferences.edit().putString("breakfast_default", oldBreakfast.name).commit()
                        throw java.io.IOException()
                    }
                }
                _selections.value = updated
                _breakfastDefault.value = backup.breakfastDefault
                com.m4sak1.ryousyoku.widget.NextMealWidgetController.refresh(getApplication())
                _backupPreview.value = null
                _backupMessage.value = "${backup.selections.size}件の記録と朝食のデフォルトを復元しました。"
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { _backupMessage.value = "復元できませんでした。端末の空き容量を確認してください。"
            } finally { _backupBusy.value = false }
        }
    }

    fun loadArchive(force: Boolean = false) {
        if (!force && _archive.value !is ArchiveState.Idle) return
        val menus = (_uiState.value as? MenuState.Success)?.allMenus?.distinctBy { it.filename } ?: return
        archiveJob?.cancel()
        _archive.value = ArchiveState.Loading
        archiveJob = viewModelScope.launch {
            val current = _structured.value as? StructuredState.Ready
            val semaphore = Semaphore(3)
            val results = coroutineScope {
                menus.map { menu -> async {
                    semaphore.withPermit {
                        try {
                            val loaded = if (current?.data?.source?.filename == menu.filename)
                                LoadedStructuredMenu(current.data, current.fromCache) else repository.fetchStructured(menu)
                            menu.filename to loaded
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            menu.filename to null
                        }
                    }
                } }.awaitAll()
            }
            _archive.value = ArchiveState.Ready(ArchiveSnapshot(
                results.mapNotNull { it.second?.menu }.sortedBy { it.period.start },
                results.filter { it.second == null }.map { it.first },
                results.filter { it.second?.fromCache == true }.map { it.first }
            ))
        }
    }

    fun prepareExport(asJson: Boolean): String? {
        val snapshot = (_archive.value as? ArchiveState.Ready)?.snapshot ?: return null
        return try {
            require(snapshot.menus.isNotEmpty())
            val now = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"))
            val export = makeNutritionExport(snapshot, _selections.value, _breakfastDefault.value,
                now.toLocalDate(), now.toOffsetDateTime().toString())
            pendingExport = if (asJson) nutritionExportJson(export) else nutritionExportText(export)
            _exportMessage.value = null
            "ryousyoku-${now.toLocalDate()}.${if (asJson) "json" else "txt"}"
        } catch (_: Exception) {
            pendingExport = null
            _exportMessage.value = "データを確認できないため書き出せません。"
            null
        }
    }

    fun saveExport(uri: Uri?) {
        val body = pendingExport
        pendingExport = null
        if (uri == null || body == null) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw java.io.IOException("No output stream")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                }
                _exportMessage.value = "書き出しました。"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _exportMessage.value = "保存できませんでした。保存先を確認してください。"
            }
        }
    }

    fun selectMenu(menu: Menu) {
        _selectedMenu.value = menu
        structuredJob?.cancel()
        structuredJob = viewModelScope.launch {
            _structured.value = StructuredState.Loading
            try {
                val loaded = repository.fetchStructured(menu)
                _structured.value = StructuredState.Ready(loaded.menu, loaded.fromCache)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _structured.value = StructuredState.Unavailable
            }
        }
    }

    fun selectOption(date: String, meal: String, option: String) {
        val current = (_structured.value as? StructuredState.Ready)?.data ?: return
        val target = current.days.find { it.date == date }?.meals?.get(meal) ?: return
        if (option != "skip" && target.options.none { it.id == option }) return
        val key = selectionKey(current, date, meal)
        val updated = _selections.value.toMutableMap()
        if (updated[key] == option) {
            updated.remove(key)
            preferences.edit().remove(key).apply()
        } else {
            updated[key] = option
            preferences.edit().putString(key, option).apply()
        }
        _selections.value = updated
        com.m4sak1.ryousyoku.widget.NextMealWidgetController.refresh(getApplication())
    }

    private val _uiState = MutableStateFlow<MenuState>(MenuState.Loading)
    val uiState: StateFlow<MenuState> = _uiState.asStateFlow()

    init {
        fetchMenus()
    }

    fun fetchMenus() {
        viewModelScope.launch {
            _uiState.value = MenuState.Loading
            try {
                val menus = repository.fetchMenus()
                if (menus.isEmpty()) {
                    _uiState.value = MenuState.Success(emptyList(), null)
                    return@launch
                }

                var activeMenu = menus.first()
                val now = Calendar.getInstance()

                // Regex: YYYYMMDD-YYYYMMDD.pdf
                val regex = Regex("^(\\d{4})(\\d{2})(\\d{2})-(\\d{4})(\\d{2})(\\d{2})\\.pdf$")

                for (menu in menus) {
                    val matchResult = regex.find(menu.filename)
                    if (matchResult != null) {
                        val sy = matchResult.groupValues[1].toInt()
                        val sm = matchResult.groupValues[2].toInt() - 1 // Calendar is 0-indexed month
                        val sd = matchResult.groupValues[3].toInt()
                        
                        val ey = matchResult.groupValues[4].toInt()
                        val em = matchResult.groupValues[5].toInt() - 1
                        val ed = matchResult.groupValues[6].toInt()

                        val startCal = Calendar.getInstance().apply {
                            set(sy, sm, sd, 0, 0, 0)
                        }
                        val endCal = Calendar.getInstance().apply {
                            set(ey, em, ed, 23, 59, 59)
                        }

                        if (now.timeInMillis in startCal.timeInMillis..endCal.timeInMillis) {
                            activeMenu = menu
                            break
                        }
                    }
                }

                _uiState.value = MenuState.Success(menus, activeMenu)
                selectMenu(activeMenu)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.value = MenuState.Error(e.message ?: "Unknown Error")
            }
        }
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
