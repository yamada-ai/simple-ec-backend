package com.example.ec.application.export

enum class AttributeExportStrategy {
    MULTISET,
    SEQUENCE_WINDOW,
    SPLITERATOR_WINDOW,
    PRELOAD,
    SQL_PIVOT,
    IMPERATIVE_RESULT_SET,
    JSON_AGGREGATION;

    companion object {
        fun from(value: String): AttributeExportStrategy {
            val normalized = value.replace("-", "_").uppercase()
            return entries.firstOrNull { it.name == normalized } ?: SEQUENCE_WINDOW
        }
    }
}
