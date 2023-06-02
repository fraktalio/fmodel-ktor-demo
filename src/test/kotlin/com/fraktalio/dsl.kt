package com.fraktalio

import com.fraktalio.fmodel.domain.IDecider
import com.fraktalio.fmodel.domain.ISaga
import com.fraktalio.fmodel.domain.IView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals

// #####  Decider DSL  ######
fun <C, S, E> IDecider<C, S, E>.givenEvents(events: Iterable<E>, command: () -> C): Flow<E> =
    decide(command(), events.fold(initialState) { s, e -> evolve(s, e) })

fun <C> whenCommand(command: C): C = command

suspend infix fun <E> Flow<E>.thenEvents(expected: Iterable<E>) = assertIterableEquals(expected, toList())

// #####  Saga DSL  ######
fun <AR, A> ISaga<AR, A>.whenActionResult(actionResults: AR) = react(actionResults)
suspend infix fun <A> Flow<A>.expectActions(expected: Iterable<A>) = assertIterableEquals(expected, toList())


// ##### View DSL ######
fun <S, E> IView<S, E>.givenEvents(events: Iterable<E>) = events.fold(initialState) { s, e -> evolve(s, e) }

infix fun <S, U : S> S.thenState(expected: U?) = assertEquals(expected, this)