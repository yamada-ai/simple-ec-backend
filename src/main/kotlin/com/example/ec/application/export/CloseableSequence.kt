package com.example.ec.application.export

import java.io.Closeable

class CloseableSequence<T>(
    private val delegate: Sequence<T>,
    private val closeAction: () -> Unit
) : Sequence<T>, Closeable {

    override fun iterator(): Iterator<T> = delegate.iterator()

    override fun close() {
        closeAction()
    }
}
