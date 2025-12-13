package com.example.ec.presentation.model

import java.util.Objects
import com.example.ec.presentation.model.OrderListResponse
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
 * @param content 
 * @param page Current page number (0-indexed)
 * @param propertySize Page size
 * @param totalPages Total number of pages
 * @param totalElements Total number of elements
 */
data class PagedOrderListResponse(

    @field:Valid
    @Schema(example = "null", required = true, description = "")
    @get:JsonProperty("content", required = true) val content: kotlin.collections.List<OrderListResponse>,

    @Schema(example = "null", required = true, description = "Current page number (0-indexed)")
    @get:JsonProperty("page", required = true) val page: kotlin.Int,

    @Schema(example = "null", required = true, description = "Page size")
    @get:JsonProperty("size", required = true) val propertySize: kotlin.Int,

    @Schema(example = "null", required = true, description = "Total number of pages")
    @get:JsonProperty("totalPages", required = true) val totalPages: kotlin.Int,

    @Schema(example = "null", required = true, description = "Total number of elements")
    @get:JsonProperty("totalElements", required = true) val totalElements: kotlin.Long
    ) {

}

