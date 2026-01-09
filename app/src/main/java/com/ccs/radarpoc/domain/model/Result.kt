package com.ccs.radarpoc.domain.model

/**
 * Generic Result wrapper for repository operations.
 * Provides a type-safe way to handle success/error states.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String = exception.message ?: "Unknown error") : Result<Nothing>()
    object Loading : Result<Nothing>()
    
    /**
     * Returns true if this result is a Success
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns true if this result is an Error
     */
    val isError: Boolean get() = this is Error
    
    /**
     * Returns the data if Success, null otherwise
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    /**
     * Returns the data if Success, or the result of onFailure otherwise
     */
    inline fun getOrElse(onFailure: (Throwable?) -> T): T = when (this) {
        is Success -> data
        is Error -> onFailure(exception)
        is Loading -> onFailure(null)
    }
    
    /**
     * Maps the success value to a new type
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }
    
    /**
     * Executes the given block if this is a Success
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * Executes the given block if this is an Error
     */
    inline fun onError(action: (Error) -> Unit): Result<T> {
        if (this is Error) action(this)
        return this
    }
}
