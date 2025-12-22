package com.example

/**
 * Example Kotlin code demonstrating various language features
 */

// Data class for representing a person
data class Person(
    val name: String,
    val age: Int,
    val email: String?
)

// Sealed class for representing results
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

// Extension function on String
fun String.isValidEmail(): Boolean {
    return this.contains("@") && this.contains(".")
}

// Higher-order function
fun <T> List<T>.customFilter(predicate: (T) -> Boolean): List<T> {
    val result = mutableListOf<T>()
    for (item in this) {
        if (predicate(item)) {
            result.add(item)
        }
    }
    return result
}

// Main function demonstrating usage
fun main() {
    // Creating instances
    val person1 = Person("Alice", 30, "alice@example.com")
    val person2 = Person("Bob", 25, null)
    
    // Using extension function
    val email = "test@example.com"
    println("Is valid email: ${email.isValidEmail()}")
    
    // Using higher-order function
    val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    val evenNumbers = numbers.customFilter { it % 2 == 0 }
    println("Even numbers: $evenNumbers")
    
    // Pattern matching with when
    val result: Result<String> = Result.Success("Data loaded")
    when (result) {
        is Result.Success -> println("Success: ${result.data}")
        is Result.Error -> println("Error: ${result.message}")
        Result.Loading -> println("Loading...")
    }
    
    // Null safety
    person2.email?.let {
        println("Email: $it")
    } ?: println("No email provided")
}

