package com.example.ec.presentation.model

import java.util.Objects
import com.example.ec.presentation.model.OrderItemRequest
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
 * @param customerId 
 * @param totalAmount 
 * @param items 
 */
data class OrderRequest(

    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("customerId", required = true) val customerId: kotlin.Long,

    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("totalAmount", required = true) val totalAmount: java.math.BigDecimal,

    @field:Valid
    @Schema(example = "null", description = "")
    @get:JsonProperty("items") val items: kotlin.collections.List<OrderItemRequest>? = null
    ) {

}

