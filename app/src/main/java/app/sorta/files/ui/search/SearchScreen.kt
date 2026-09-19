package app.sorta.files.ui.search

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.SortaApp
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.fs.FileTypeCategory
import app.sorta.files.core.fs.MimeUtil
import app.sorta.files.ui.FileActions
import app.sorta.files.ui.components.EmptyState
import app.sorta.files.ui.components.FileRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File

enum class DateFilter(val days: Int?) { ANY(null), TODAY(0), D7(7), D30(30) }
enum class SizeFilter(val lo: Long, val hi: Long) {
    ANY(0, Long.MAX_VALUE), LT1(0, 1L shl 20),
    M1_10(1L shl 20, 10L shl 20), M10_100(10L shl 20, 100L shl 20),
    GT100(100L shl 20, Long.MAX_VALUE)
}
enum class LocFilter { ALL, VOLUME }

data class SearchFilters(
    val type: FileTypeCategory? = null,
    val date: DateFilter = DateFilter.ANY,
    val size: SizeFilter = SizeFilter.ANY,
    val loc: LocFilter = LocFilter.ALL,
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val _results = MutableStateFlow<List<FileItem>>(emptyList())
    val results: StateFlow<List<FileItem>> = _results
    val query = MutableStateFlow("")
    val filters = MutableStateFlow(SearchFilters())
    val searching = MutableStateFlow(false)

    private var job: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            query.debounce(300).collect { runSearch() }
        }
        viewModelScope.launch { filters.collect { runSearch() } }
    }

    private fun runSearch() {
        job?.cancel()
        val q = query.value.trim()
        if (q.isEmpty()) { _results.value = emptyList(); return }
        searching.value = true
        job = viewModelScope.launch(Dispatchers.IO) {
            val f = filters.value
            val roots = if (f.loc == LocFilter.VOLUME) {
                listOf(android.os.Environment.getExternalStorageDirectory())
            } else {
                app.sorta.files.core.fs.StorageVolumes.list(getApplication())
                    .map { java.io.File(it.path) }
            }
            val out = mutableListOf<FileItem>()
            val now = System.currentTimeMillis()
            val dayStart = now - (now % 86400000) // approx; fine for filter
            val extQuery = q.startsWith(".") && q.length > 1

            roots.forEach { root ->
                root.walkTopDown()
                    .onEnter { it.name != ".sorta_trash" && (!it.isHidden || it == root) }
                    .filter { it.isFile }
                    .filter { fi ->
                        if (extQuery) fi.name.endsWith(q, ignoreCase = true)
                        else fi.name.contains(q, ignoreCase = true)
                    }
                    .filter { fi ->
                        val cat = MimeUtil.categoryOf(fi.name, false)
                        (f.type == null || cat == f.type) &&
                            (f.size == SizeFilter.ANY || fi.length() in f.size.lo..f.size.hi) &&
                            when (f.date) {
                                DateFilter.ANY -> true
                                DateFilter.TODAY -> fi.lastModified() >= dayStart
                                DateFilter.D7 -> fi.lastModified() >= now - 7L * 86400000
                                DateFilter.D30 -> fi.lastModified() >= now - 30L * 86400000
                            }
                    }
                    .take(2000)
                    .forEach {
                        out += FileSystem.toItem(it)
                        if (out.size % 50 == 0) _results.value = out.toList()
                    }
            }
            _results.value = out.sortedByDescending { it.lastModified }
            searching.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavController, container: AppContainer) {
    val vm: SearchViewModel = viewModel()
    val context = LocalContext.current
    val results by vm.results.collectAsState()
    val q by vm.query.collectAsState()
    val f by vm.filters.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = q, onValueChange = { vm.query.value = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                }
            },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterRow(
                labels = listOf(null to R.string.filter_type,
                    FileTypeCategory.IMAGE to R.string.cat_images,
                    FileTypeCategory.VIDEO to R.string.cat_videos,
                    FileTypeCategory.AUDIO to R.string.cat_audio,
                    FileTypeCategory.DOCUMENT to R.string.cat_documents,
                    FileTypeCategory.APK to R.string.cat_apks,
                    FileTypeCategory.ARCHIVE to R.string.cat_archives),
                selected = f.type,
                onSelect = { vm.filters.value = f.copy(type = it) },
            )
            FilterRow(
                labels = DateFilter.entries.map {
                    it to when (it) {
                        DateFilter.ANY -> R.string.date_any
                        DateFilter.TODAY -> R.string.date_today
                        DateFilter.D7 -> R.string.date_7d
                        DateFilter.D30 -> R.string.date_30d
                    }
                },
                selected = f.date,
                onSelect = { vm.filters.value = f.copy(date = it) },
            )
            FilterRow(
                labels = SizeFilter.entries.map {
                    it to when (it) {
                        SizeFilter.ANY -> R.string.size_any
                        SizeFilter.LT1 -> R.string.size_lt1
                        SizeFilter.M1_10 -> R.string.size_1_10
                        SizeFilter.M10_100 -> R.string.size_10_100
                        SizeFilter.GT100 -> R.string.size_gt100
                    }
                },
                selected = f.size,
                onSelect = { vm.filters.value = f.copy(size = it) },
            )
            FilterRow(
                labels = LocFilter.entries.map {
                    it to if (it == LocFilter.ALL) R.string.loc_all else R.string.loc_volume
                },
                selected = f.loc,
                onSelect = { vm.filters.value = f.copy(loc = it) },
            )
            if (q.isNotEmpty() && results.isEmpty()) EmptyState(stringResource(R.string.empty_results))
            else LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.path }) { item ->
                    FileRow(item, false, false,
                        onClick = { FileActions.open(context, nav, item) },
                        onLongClick = { })
                }
            }
        }
    }
}

@Composable
private fun <T> FilterRow(
    labels: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        labels.forEach { (v, label) ->
            FilterChip(
                selected = selected == v,
                onClick = { onSelect(v) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
