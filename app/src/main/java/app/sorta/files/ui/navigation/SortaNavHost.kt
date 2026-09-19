package app.sorta.files.ui.navigation

import android.net.Uri

object Dest {
    const val HOME = "home"
    const val INBOX = "inbox"
    const val BROWSE = "browse"
    const val STORAGE = "storage"

    const val FOLDER = "folder/{path}"
    const val CATEGORY = "category/{cat}"
    const val SEARCH = "search"
    const val RECENT = "recent"
    const val TRASH = "trash"
    const val HISTORY = "history"
    const val BATCH_RENAME = "rename/{paths}"
    const val RULES = "rules"
    const val RULE_PREVIEW = "rule_preview"
    const val INBOX_SOURCES = "inbox_sources"
    const val IMAGE_PREVIEW = "imgprev/{path}"
    const val TEXT_PREVIEW = "txtprev/{path}"
    const val PDF_PREVIEW = "pdfprev/{path}"

    fun folder(path: String) = "folder/${Uri.encode(path)}"
    fun category(cat: String) = "category/$cat"
    fun imagePreview(path: String) = "imgprev/${Uri.encode(path)}"
    fun textPreview(path: String) = "txtprev/${Uri.encode(path)}"
    fun pdfPreview(path: String) = "pdfprev/${Uri.encode(path)}"
    fun batchRename(paths: List<String>) = "rename/${Uri.encode(paths.joinToString("|"))}"

    fun decode(arg: String?): String = Uri.decode(arg ?: "")
}
