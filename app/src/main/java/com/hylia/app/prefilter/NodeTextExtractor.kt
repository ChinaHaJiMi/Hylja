package com.hylia.app.prefilter

import android.view.accessibility.AccessibilityNodeInfo

/** 从无障碍节点树中尽可能多地抽取可见文本。 */
object NodeTextExtractor {

    private const val MAX_TEXTS = 48
    private const val MAX_LEN = 200
    private const val MIN_LEN = 2

    fun extract(source: AccessibilityNodeInfo?): List<String> {
        if (source == null) return emptyList()
        val out = ArrayList<String>(MAX_TEXTS)
        walk(source, out)
        return out
            .distinct()
            .filter { it.length in MIN_LEN..MAX_LEN }
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<String>) {
        if (out.size >= MAX_TEXTS) return
        if (node.isVisibleToUser) {
            node.text
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.trim()
                ?.let { out.add(it) }
        }
        for (i in 0 until node.childCount) {
            if (out.size >= MAX_TEXTS) break
            node.getChild(i)?.let { walk(it, out) }
        }
    }
}