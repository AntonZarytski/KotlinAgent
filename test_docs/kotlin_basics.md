# Kotlin Programming Basics

## Introduction to Kotlin

Kotlin is a modern, statically-typed programming language that runs on the Java Virtual Machine (JVM). It was developed by JetBrains and officially supported by Google for Android development.

## Key Features

### Null Safety
Kotlin's type system is designed to eliminate null pointer exceptions. Variables must be explicitly marked as nullable:

```kotlin
var name: String = "John"  // Cannot be null
var nullableName: String? = null  // Can be null
```

### Data Classes
Kotlin provides a concise way to create classes that hold data:

```kotlin
data class User(val name: String, val age: Int)
```

This automatically generates equals(), hashCode(), toString(), and copy() methods.

### Extension Functions
You can extend existing classes with new functionality without inheritance:

```kotlin
fun String.addExclamation() = this + "!"
println("Hello".addExclamation())  // Prints: Hello!
```

### Coroutines
Kotlin has built-in support for asynchronous programming through coroutines:

```kotlin
suspend fun fetchData(): String {
    delay(1000)
    return "Data"
}
```

## Collections

Kotlin distinguishes between mutable and immutable collections:

- `List<T>` - Immutable list
- `MutableList<T>` - Mutable list
- `Set<T>` - Immutable set
- `MutableSet<T>` - Mutable set
- `Map<K, V>` - Immutable map
- `MutableMap<K, V>` - Mutable map

## When Expression

The `when` expression is a powerful replacement for switch statements:

```kotlin
when (x) {
    1 -> print("x is 1")
    2 -> print("x is 2")
    else -> print("x is neither 1 nor 2")
}
```

## Smart Casts

Kotlin automatically casts variables after type checks:

```kotlin
if (obj is String) {
    // obj is automatically cast to String
    print(obj.length)
}
```

