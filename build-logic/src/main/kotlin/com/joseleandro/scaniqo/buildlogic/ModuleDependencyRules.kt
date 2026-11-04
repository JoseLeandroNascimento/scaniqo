package com.joseleandro.scaniqo.buildlogic

internal object ModuleDependencyRules {
    fun violations(
        source: String,
        targets: List<String>,
        configurationName: String = "implementation",
    ): List<String> = targets.distinct().filter { target ->
        when {
            source == target -> false
            target == ":core:testing" -> !isTestConfiguration(configurationName)
            source == ":app" -> false
            target == ":app" -> true
            source.startsWith(":core:") &&
                (target.startsWith(":feature:") || target.startsWith(":data:")) -> true
            source.startsWith(":data:") && target.startsWith(":feature:") -> true
            source.startsWith(":feature:") && target.startsWith(":data:") -> true
            source.startsWith(":feature:") && target.startsWith(":feature:") ->
                source != ":feature:editor" || target != ":feature:signatures"
            else -> false
        }
    }.sorted().map { target -> "$source -> $target" }

    private val camelCaseBoundary = Regex("([a-z0-9])([A-Z])")

    private fun isTestConfiguration(name: String): Boolean =
        name.replace(camelCaseBoundary, "$1 $2").split(' ', '-', '_').any {
            it.equals("test", ignoreCase = true) || it.equals("tests", ignoreCase = true)
        }
}
