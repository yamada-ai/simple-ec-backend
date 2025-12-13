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
 * @param customers Total number of customers
 * @param orders Total number of orders
 * @param orderItems Total number of order items
 */
data class AdminSummaryResponse(

    @Schema(example = "null", required = true, description = "Total number of customers")
    @get:JsonProperty("customers", required = true) val customers: kotlin.Long,

    @Schema(example = "null", required = true, description = "Total number of orders")
    @get:JsonProperty("orders", required = true) val orders: kotlin.Long,

    @Schema(example = "null", required = true, description = "Total number of order items")
    @get:JsonProperty("orderItems", required = true) val orderItems: kotlin.Long
    ) {

}

