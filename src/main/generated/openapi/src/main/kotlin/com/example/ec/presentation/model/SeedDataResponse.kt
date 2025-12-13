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
 * @param customersCreated Number of customers created
 * @param ordersCreated Number of orders created
 * @param orderItemsCreated Number of order items created
 * @param attributeDefinitionsCreated Number of attribute definitions created
 * @param attributeValuesCreated Number of attribute values created
 */
data class SeedDataResponse(

    @Schema(example = "null", required = true, description = "Number of customers created")
    @get:JsonProperty("customersCreated", required = true) val customersCreated: kotlin.Int,

    @Schema(example = "null", required = true, description = "Number of orders created")
    @get:JsonProperty("ordersCreated", required = true) val ordersCreated: kotlin.Int,

    @Schema(example = "null", required = true, description = "Number of order items created")
    @get:JsonProperty("orderItemsCreated", required = true) val orderItemsCreated: kotlin.Int,

    @Schema(example = "null", required = true, description = "Number of attribute definitions created")
    @get:JsonProperty("attributeDefinitionsCreated", required = true) val attributeDefinitionsCreated: kotlin.Int,

    @Schema(example = "null", required = true, description = "Number of attribute values created")
    @get:JsonProperty("attributeValuesCreated", required = true) val attributeValuesCreated: kotlin.Int
    ) {

}

