package app.sorta.files.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.sorta.files.SortaApp
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.fs.SortField
import app.sorta.files.core.fs.SortSpec
import app.sorta.files.core.fs.StorageVolumes
import app.sorta.files.core.fs.VolumeInfo
import app.sorta.files.data.db.FolderPref
import app.sorta.files.data.db.RecentLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class BrowseViewModel(app: Application) : AndroidViewModel(app) {
    private val container get() = (getApplication<SortaApp>()).container
    private val prefDao get() = container.db.folderPrefDao()
    private val locDao get() = container.db.recentLocationDao()

    private val _volumes = MutableStateFlow<List<VolumeInfo>>(emptyList())
    val volumes: StateFlow<List<VolumeInfo>> = _volumes

    private val _items = MutableStateFlow<List<FileItem>>(emptyList())
    val items: StateFlow<List<FileItem>> = _items

    private val _currentDir = MutableStateFlow<String?>(null)
    val currentDir: StateFlow<String?> = _currentDir

    private val _restricted = MutableStateFlow(false)
    val restricted: StateFlow<Boolean> = _restricted

    val sort = MutableStateFlow(SortSpec())
    val grid = MutableStateFlow(false)
    val showHidden = MutableStateFlow(false)

    // selection
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection
    private var lastLongPressIndex: Int? = null

    // restored scroll
    var restoredScroll: Pair<Int, Int>? = null

    private var prefsLoaded = false

    init {
        viewModelScope.launch {
            _volumes.value = StorageVolumes.list(app)
            showHidden.value = container.prefs.showHidden.first()
            grid.value = container.prefs.defaultViewGrid.first()
        }
    }

    fun loadFolder(path: String) {
        _currentDir.value = path
        _selection.value = emptySet()
        lastLongPressIndex = null
        viewModelScope.launch(Dispatchers.IO) {
            if (!prefsLoaded || true) {
                prefDao.get(path)?.let { p ->
                    sort.value = SortSpec(
                        SortField.valueOf(p.sortField), p.ascending)
                    grid.value = p.grid
                    restoredScroll = p.scrollIndex to p.scrollOffset
                }
            }
            prefsLoaded = true
            _restricted.value = app.sorta.files.core.fs.RestrictedPaths.isRestricted(path) ||
                app.sorta.files.core.fs.FileSystem.isUnreadable(path)
            _items.value = FileSystem.list(path, showHidden.value, sort.value)
            locDao.upsert(RecentLocation(path,
                File(path).name.ifEmpty { path }.let { n ->
                    if (n == "0") "Internal storage" else n }))
        }
    }

    fun reload() = _currentDir.value?.let { loadFolder(it) }

    fun setSort(s: SortSpec) { sort.value = s; reload(); savePref() }
    fun setGrid(g: Boolean) { grid.value = g; savePref() }
    fun setShowHidden(v: Boolean) {
        showHidden.value = v
        viewModelScope.launch { container.prefs.setShowHidden(v) }
        reload()
    }

    fun saveScroll(index: Int, offset: Int) {
        val path = _currentDir.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.upsert(FolderPref(path, sort.value.field.name, sort.value.ascending,
                grid.value, index, offset))
        }
    }

    private fun savePref() {
        val path = _currentDir.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.upsert(FolderPref(path, sort.value.field.name, sort.value.ascending, grid.value))
        }
    }

    fun toggleSelect(item: FileItem) {
        val cur = _selection.value.toMutableSet()
        if (!cur.add(item.path)) cur.remove(item.path)
        _selection.value = cur
    }

    fun longPress(item: FileItem) {
        val list = _items.value
        val idx = list.indexOfFirst { it.path == item.path }
        val anchor = lastLongPressIndex
        if (_selection.value.isNotEmpty() && anchor != null && idx >= 0) {
            val range = minOf(anchor, idx)..maxOf(anchor, idx)
            _selection.value = _selection.value + range.map { list[it].path }
        } else {
            toggleSelect(item)
        }
        lastLongPressIndex = idx
    }

    fun selectAll() { _selection.value = _items.value.map { it.path }.toSet() }
    fun clearSelection() { _selection.value = emptySet(); lastLongPressIndex = null }

    fun selectedItems(): List<FileItem> =
        _items.value.filter { it.path in _selection.value }

    fun addFavorite(item: FileItem) {
        viewModelScope.launch {
            container.db.favoriteFolderDao().upsert(
                app.sorta.files.data.db.FavoriteFolder(item.path, item.name))
        }
    }
}
