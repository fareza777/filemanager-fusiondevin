package app.sorta.files.ui.basket

import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.ops.OpType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BasketItem(val item: FileItem, val op: OpType)

/** Cross-folder "cart" of items + intended op (copy/move). */
object SelectionBasket {
    private val _items = MutableStateFlow<List<BasketItem>>(emptyList())
    val items: StateFlow<List<BasketItem>> = _items

    fun set(items: List<FileItem>, op: OpType) {
        _items.value = items.map { BasketItem(it, op) }
    }

    fun add(items: List<FileItem>, op: OpType) {
        val cur = _items.value.toMutableList()
        items.forEach { f ->
            if (cur.none { it.item.path == f.path }) cur += BasketItem(f, op)
        }
        _items.value = cur
    }

    fun clear() { _items.value = emptyList() }
}
