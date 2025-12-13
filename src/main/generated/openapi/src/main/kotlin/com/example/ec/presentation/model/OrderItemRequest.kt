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
 * @param productName 
 * @param quantity 
 * @param unitPrice 
 */
data class OrderItemRequest(

    @get:Size(max=255)
    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("productName", required = true) val productName: kotlin.String,

    @get:Min(1)
    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("quantity", required = true) val quantity: kotlin.Int,

    @get:DecimalMin("0")
    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("unitPrice", required = true) val unitPrice: java.math.BigDecimal
    ) {

}

