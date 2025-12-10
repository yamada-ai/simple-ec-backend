package com.example.ec.infrastructure.repository.attribute

import com.example.ec.domain.attribute.OrderAttributeDefinition
import com.example.ec.domain.attribute.OrderAttributeDefinitionRepository
import com.example.ec.domain.shared.ID
import com.example.ec.infrastructure.jooq.tables.records.OrderAttributeDefinitionRecord
import com.example.ec.infrastructure.jooq.tables.references.ORDER_ATTRIBUTE_DEFINITION
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class OrderAttributeDefinitionRepositoryImpl(
    private val dsl: DSLContext
) : OrderAttributeDefinitionRepository {

    override fun findAll(): List<OrderAttributeDefinition> {
        return dsl.selectFrom(ORDER_ATTRIBUTE_DEFINITION)
            .orderBy(ORDER_ATTRIBUTE_DEFINITION.ID.asc())
            .fetch()
            .map { convertToDomain(it) }
    }

    override fun findById(id: ID<OrderAttributeDefinition>): OrderAttributeDefinition? {
        return dsl.selectFrom(ORDER_ATTRIBUTE_DEFINITION)
            .where(ORDER_ATTRIBUTE_DEFINITION.ID.eq(id.value))
            .fetchOne()
            ?.let { convertToDomain(it) }
    }

    override fun findByName(name: String): OrderAttributeDefinition? {
        return dsl.selectFrom(ORDER_ATTRIBUTE_DEFINITION)
            .where(ORDER_ATTRIBUTE_DEFINITION.NAME.eq(name))
            .fetchOne()
            ?.let { convertToDomain(it) }
    }

    override fun save(definition: OrderAttributeDefinition): OrderAttributeDefinition {
        // IDが0の場合は新規作成、それ以外は更新
        return if (definition.id.value == 0L) {
            insert(definition)
        } else {
            update(definition)
        }
    }

    private fun insert(definition: OrderAttributeDefinition): OrderAttributeDefinition {
        val id = dsl.insertInto(ORDER_ATTRIBUTE_DEFINITION)
            .set(ORDER_ATTRIBUTE_DEFINITION.NAME, definition.name)
            .set(ORDER_ATTRIBUTE_DEFINITION.LABEL, definition.label)
            .set(ORDER_ATTRIBUTE_DEFINITION.DESCRIPTION, definition.description)
            .set(ORDER_ATTRIBUTE_DEFINITION.CREATED_AT, definition.createdAt)
            .returningResult(ORDER_ATTRIBUTE_DEFINITION.ID)
            .fetchOne()
            ?.value1()
            ?: error("Failed to insert order attribute definition")

        return definition.copy(id = ID(id))
    }

    private fun update(definition: OrderAttributeDefinition): OrderAttributeDefinition {
        val updated = dsl.update(ORDER_ATTRIBUTE_DEFINITION)
            .set(ORDER_ATTRIBUTE_DEFINITION.NAME, definition.name)
            .set(ORDER_ATTRIBUTE_DEFINITION.LABEL, definition.label)
            .set(ORDER_ATTRIBUTE_DEFINITION.DESCRIPTION, definition.description)
            .where(ORDER_ATTRIBUTE_DEFINITION.ID.eq(definition.id.value))
            .execute()

        if (updated == 0) {
            error("Order attribute definition not found: id=${definition.id.value}")
        }

        return definition
    }

    override fun deleteById(id: ID<OrderAttributeDefinition>): Int {
        return dsl.deleteFrom(ORDER_ATTRIBUTE_DEFINITION)
            .where(ORDER_ATTRIBUTE_DEFINITION.ID.eq(id.value))
            .execute()
    }

    override fun count(): Long {
        return dsl.selectCount()
            .from(ORDER_ATTRIBUTE_DEFINITION)
            .fetchOne(0, Long::class.java) ?: 0L
    }

    private fun convertToDomain(record: OrderAttributeDefinitionRecord): OrderAttributeDefinition {
        return OrderAttributeDefinition(
            id = ID(record.id!!),
            name = record.name!!,
            label = record.label!!,
            description = record.description,
            createdAt = record.createdAt!!
        )
    }
}
