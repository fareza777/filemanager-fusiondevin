package app.sorta.files.core.rules

import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.MimeUtil
import app.sorta.files.data.db.SortRule
import java.io.File

object RuleEngine {

    fun matches(rule: SortRule, item: FileItem): Boolean = when (rule.matchType) {
        "EXT" -> MimeUtil.extension(item.name)
            .equals(rule.matchValue.trimStart('.'), ignoreCase = true)
        "NAME_CONTAINS" -> item.name.contains(rule.matchValue, ignoreCase = true)
        "NAME_REGEX", "REGEX" -> try {
            Regex(rule.matchValue, RegexOption.IGNORE_CASE).containsMatchIn(item.name)
        } catch (_: Exception) { false }
        else -> false
    }

    /** First enabled matching rule wins (rules evaluated in given order). */
    fun firstMatch(rules: List<SortRule>, item: FileItem): SortRule? =
        rules.firstOrNull { it.enabled && matches(it, item) }

    data class RuleMatch(val item: FileItem, val rule: SortRule)

    fun preview(rules: List<SortRule>, items: List<FileItem>): List<RuleMatch> =
        items.mapNotNull { item -> firstMatch(rules, item)?.let { RuleMatch(item, it) } }
}
