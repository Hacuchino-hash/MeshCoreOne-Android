// PortedFrom: MC1Tests/Helpers/TestHelpers.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.testing

import java.util.concurrent.atomic.AtomicReference

class MutableBox<T>(initialValue: T) {
    private val storage = AtomicReference(initialValue)

    var value: T
        get() = storage.get()
        set(value) = storage.set(value)
}
