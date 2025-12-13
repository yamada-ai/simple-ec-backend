package com.example.ec.presentation.model

import java.util.Objects
import com.example.ec.presentation.model.CustomerSummary
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
 * @param customer 
 * @param orderDate 
 * @param totalAmount 
 * @param items 
 * @param createdAt 
 */
data class OrderDetailResponse(

    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("id", required = true) val id: kotlin.Long,

    @field:Valid
    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("customer", required = true) val customer: CustomerSummary,

    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("orderDate", required = true) val orderDate: java.time.OffsetDateTime,

    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("totalAmount", required = true) val totalAmount: kotlin.Double,

    @field:Valid
    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("items", required = true) val items: kotlin.collections.List<OrderItemSummary>,

    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("createdAt", required = true) val createdAt: java.time.OffsetDateTime
    ) {

}

