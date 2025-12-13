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
 * @param name Internal name (unique, e.g., gift_wrapping_type)
 * @param label Display label (e.g., Gift Wrapping Type)
 * @param description 
 */
data class CreateAttributeDefinitionRequest(

    @get:Size(max=100)
    @Schema(example = "null", required = true, description = "Internal name (unique, e.g., gift_wrapping_type)")
    @get:JsonProperty("name", required = true) val name: kotlin.String,

    @get:Size(max=100)
    @Schema(example = "null", required = true, description = "Display label (e.g., Gift Wrapping Type)")
    @get:JsonProperty("label", required = true) val label: kotlin.String,

    @get:Size(max=255)
    @Schema(example = "null", description = "")
    @get:JsonProperty("description") val description: kotlin.String? = null
    ) {

}

