package com.example.ec.presentation.exception

/**
 * リソースが見つからない場合の例外 (HTTP 404)
 */
class NotFoundException(message: String) : RuntimeException(message)

/**
 * リソースの競合が発生した場合の例外 (HTTP 409)
 */
class ConflictException(message: String) : RuntimeException(message)
