package com.example.ec.presentation.controller.attribute

import com.example.ec.application.attribute.AttributeDefinitionService
import com.example.ec.presentation.mapper.toResponse
import com.example.ec.presentation.model.AttributeDefinitionResponse
import com.example.ec.presentation.model.CreateAttributeDefinitionRequest
import com.example.ec.presentation.model.UpdateAttributeDefinitionRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 注文属性定義 CRUD API コントローラー
 *
 * OpenAPI生成のAttributesApiインターフェースは仕様参照のみに使用し、
 * Spring MVCのルーティングは自前で定義する。
 */
@RestController
@RequestMapping("/api/attribute-definitions")
class AttributeDefinitionController(
    private val service: AttributeDefinitionService
) {

    @GetMapping
    fun listAll(): ResponseEntity<List<AttributeDefinitionResponse>> {
        val definitions = service.listAll()
        val responses = definitions.map { it.toResponse() }
        return ResponseEntity.ok(responses)
    }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<AttributeDefinitionResponse> {
        val definition = service.findById(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(definition.toResponse())
    }

    @PostMapping
    fun create(@RequestBody request: CreateAttributeDefinitionRequest): ResponseEntity<AttributeDefinitionResponse> {
        val definition = service.create(
            name = request.name,
            label = request.label,
            description = request.description
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(definition.toResponse())
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: UpdateAttributeDefinitionRequest
    ): ResponseEntity<AttributeDefinitionResponse> {
        val definition = service.update(
            id = id,
            name = request.name,
            label = request.label,
            description = request.description
        )

        return ResponseEntity.ok(definition.toResponse())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }
}
