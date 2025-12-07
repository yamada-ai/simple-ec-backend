package com.example.ec.infrastructure.repository.customer

import com.example.ec.domain.customer.Customer
import com.example.ec.domain.customer.CustomerRepository
import com.example.ec.domain.shared.Email
import com.example.ec.domain.shared.ID
import com.example.ec.infrastructure.jooq.tables.records.CustomerRecord
import com.example.ec.infrastructure.jooq.tables.references.CUSTOMER
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class CustomerRepositoryImpl(
    private val dsl: DSLContext
) : CustomerRepository {

    override fun findById(id: ID<Customer>): Customer? {
        return dsl.selectFrom(CUSTOMER)
            .where(CUSTOMER.ID.eq(id.value))
            .fetchOne()
            ?.let { convertToCustomer(it) }
    }

    override fun findByEmail(email: Email): Customer? {
        return dsl.selectFrom(CUSTOMER)
            .where(CUSTOMER.EMAIL.eq(email.value))
            .fetchOne()
            ?.let { convertToCustomer(it) }
    }

    override fun count(): Long {
        return dsl.selectCount()
            .from(CUSTOMER)
            .fetchOne(0, Long::class.java) ?: 0L
    }

    private fun convertToCustomer(record: CustomerRecord): Customer {
        return Customer(
            id = ID(record.id!!),
            name = record.name!!,
            email = Email(record.email!!),
            createdAt = record.createdAt!!
        )
    }
}
