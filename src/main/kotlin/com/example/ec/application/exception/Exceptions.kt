package com.example.ec.application.exception

/**
 * リソースが見つからない場合の例外。
 */
class NotFoundException(message: String) : RuntimeException(message)

/**
 * リソースの競合が発生した場合の例外。
 */
class ConflictException(message: String) : RuntimeException(message)
