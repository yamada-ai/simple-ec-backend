package com.example.ec.application.attribute

import com.example.ec.domain.attribute.OrderAttributeDefinition
import com.example.ec.domain.attribute.OrderAttributeDefinitionRepository
import com.example.ec.domain.shared.ID
import com.example.ec.presentation.exception.ConflictException
import com.example.ec.presentation.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 注文属性定義サービス
 */
@Service
class AttributeDefinitionService(
    private val definitionRepository: OrderAttributeDefinitionRepository
) {
    /**
     * 全ての属性定義を取得する
     */
    fun listAll(): List<OrderAttributeDefinition> {
        return definitionRepository.findAll()
    }

    /**
     * IDで属性定義を取得する
     */
    fun findById(id: Long): OrderAttributeDefinition? {
        return definitionRepository.findById(ID(id))
    }

    /**
     * 新しい属性定義を作成する
     */
    @Transactional
    fun create(name: String, label: String, description: String?): OrderAttributeDefinition {
        // name の一意性チェック
        definitionRepository.findByName(name)?.let {
            throw ConflictException("Attribute definition with name '$name' already exists")
        }

        val definition = OrderAttributeDefinition(
            id = ID(0), // 新規作成時は0
            name = name,
            label = label,
            description = description,
            createdAt = LocalDateTime.now()
        )

        return definitionRepository.save(definition)
    }

    /**
     * 属性定義を更新する
     */
    @Transactional
    fun update(id: Long, name: String, label: String, description: String?): OrderAttributeDefinition {
        val existing = definitionRepository.findById(ID(id))
            ?: throw NotFoundException("Attribute definition not found: id=$id")

        // name が変更された場合、一意性チェック
        if (existing.name != name) {
            definitionRepository.findByName(name)?.let {
                throw ConflictException("Attribute definition with name '$name' already exists")
            }
        }

        val updated = existing.copy(
            name = name,
            label = label,
            description = description
        )

        return definitionRepository.save(updated)
    }

    /**
     * 属性定義を削除する（関連する属性値も削除される）
     */
    @Transactional
    fun delete(id: Long) {
        val deleted = definitionRepository.deleteById(ID(id))
        if (deleted == 0) {
            throw NotFoundException("Attribute definition not found: id=$id")
        }
    }
}
