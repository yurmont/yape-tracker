package io.github.yurmont.yapetracker

object Config {
    /** Yape's Android package. Unconfirmed on a real phone: check it if incomes don't arrive. */
    val YAPE_PACKAGES = setOf("com.bcp.innovacxion.yapeapp")

    /** Identical income notifications within this window are saved once. */
    const val INCOME_DEDUP_WINDOW_MS = 2 * 60 * 1000L
}
