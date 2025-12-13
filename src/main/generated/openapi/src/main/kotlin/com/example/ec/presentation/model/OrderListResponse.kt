package com.example.ec.presentation.model

import java.util.Objects
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
 * @param customerName Customer name
 * @param orderDate 
 * @param totalAmount 
 * @param itemCount Number of items in the order
 */
data class OrderListResponse(

    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("id", required = true) val id: kotlin.Long,

    @Schema(example = "null", required = true, description = "Customer name")
    @get:JsonProperty("customerName", required = true) val customerName: kotlin.String,

    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("orderDate", required = true) val orderDate: java.time.OffsetDateTime,

    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("totalAmount", required = true) val totalAmount: kotlin.Double,

    @Schema(example = "null", required = true, description = "Number of items in the order")
    @get:JsonProperty("itemCount", required = true) val itemCount: kotlin.Int
    ) {

}

