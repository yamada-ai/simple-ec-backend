package com.example.ec.presentation.model

import java.util.Objects
import com.example.ec.presentation.model.OrderItemSummary
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 
 * @param id 
 * @param customerId 
 * @param orderDate 
 * @param totalAmount 
 * @param createdAt 
 * @param items 
 */
data class OrderResponse(

    @Schema(example = "null", description = "")
    @get:JsonProperty("id") val id: kotlin.Long? = null,

    @Schema(example = "null", description = "")
    @get:JsonProperty("customerId") val customerId: kotlin.Long? = null,

    @Schema(example = "null", description = "")
    @get:JsonProperty("orderDate") val orderDate: java.time.OffsetDateTime? = null,

    @Schema(example = "null", description = "")
    @get:JsonProperty("totalAmount") val totalAmount: java.math.BigDecimal? = null,

    @Schema(example = "null", description = "")
    @get:JsonProperty("createdAt") val createdAt: java.time.OffsetDateTime? = null,

    @field:Valid
    @Schema(example = "null", description = "")
    @get:JsonProperty("items") val items: kotlin.collections.List<OrderItemSummary>? = null
    ) {

}

