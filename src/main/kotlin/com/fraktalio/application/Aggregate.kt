package com.fraktalio.application

import com.fraktalio.domain.*
import com.fraktalio.fmodel.application.EventLockingRepository
import com.fraktalio.fmodel.application.EventSourcingLockingOrchestratingAggregate
import com.fraktalio.fmodel.domain.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.*

internal typealias AggregateEventRepository = EventLockingRepository<Command?, Event?, UUID?>
internal typealias Aggregate = EventSourcingLockingOrchestratingAggregate<Command?, Pair<Order?, Restaurant?>, Event?, UUID?>

/**
 * One, big aggregate that is `combining` all deciders: [orderDecider], [restaurantDecider].
 * Every command will be handled by one of the deciders.
 * The decider that is not interested in specific command type will simply ignore it (do nothing).
 *
 * @param orderDecider orderDecider is used internally to handle commands and produce new events.
 * @param restaurantDecider restaurantDecider is used internally to handle commands and produce new events.
 * @param orderSaga orderSaga is used internally to react on [RestaurantEvent]s and produce commands of type [OrderCommand]
 * @param restaurantSaga restaurantSaga is used internally to react on [OrderEvent]s and produce commands of type [RestaurantCommand]
 * @param eventRepository is used to store the newly produced events of the Restaurant and/or Restaurant order together
 *
 * @author Иван Дугалић / Ivan Dugalic / @idugalic
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun aggregate(
    orderDecider: OrderDecider,
    restaurantDecider: RestaurantDecider,
    orderSaga: OrderSaga,
    restaurantSaga: RestaurantSaga,
    eventRepository: AggregateEventRepository

): Aggregate = EventSourcingLockingOrchestratingAggregate(
    // Combining two deciders into one.
    decider = orderDecider.combine(restaurantDecider),
    // How and where do you want to store new events.
    eventRepository = eventRepository,
    // Combining individual choreography Sagas into one orchestrating Saga.
    saga = orderSaga.combine(restaurantSaga)
)



