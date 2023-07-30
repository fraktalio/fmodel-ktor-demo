package com.fraktalio.domain

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.util.*

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
}

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): BigDecimal = decoder.decodeString().toBigDecimal()
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
}

class ImmutableListSerializer<T>(elementSerializer: KSerializer<T>) : KSerializer<ImmutableList<T>> {
    private val delegateSerializer = ListSerializer(elementSerializer)

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        SerialDescriptor("ImmutableList", elementSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: ImmutableList<T>) =
        encoder.encodeSerializableValue(delegateSerializer, value)

    override fun deserialize(decoder: Decoder) = decoder.decodeSerializableValue(delegateSerializer)
        .toImmutableList()
}

@Serializable
@JvmInline
value class RestaurantId(@Serializable(with = UUIDSerializer::class) val value: UUID = UUID.randomUUID())

@Serializable
@JvmInline
value class RestaurantName(val value: String)

@Serializable
@JvmInline
value class OrderId(@Serializable(with = UUIDSerializer::class) val value: UUID = UUID.randomUUID())

@Serializable
@JvmInline
value class Reason(val value: String)

@Serializable
@JvmInline
value class Money(@Serializable(with = BigDecimalSerializer::class) val value: BigDecimal) {

    operator fun plus(delta: Money): Money {
        return Money(value.add(delta.value))
    }

    operator fun times(x: Int): Money {
        return Money(value.multiply(BigDecimal(x)))
    }
}

@Serializable
@JvmInline
value class MenuItemId(val value: String)

@Serializable
@JvmInline
value class MenuItemName(val value: String)

@Serializable
data class MenuItem(val menuItemId: MenuItemId, val name: MenuItemName, val price: Money)

@Serializable
enum class RestaurantMenuCuisine {
    SERBIAN,
    ITALIAN,
    INDIAN,
    TURKISH,
    GENERAL
}

@Serializable
data class RestaurantMenu(
    @Serializable(with = ImmutableListSerializer::class)
    val menuItems: ImmutableList<MenuItem>,
    @Serializable(with = UUIDSerializer::class)
    val menuId: UUID = UUID.randomUUID(),
    val cuisine: RestaurantMenuCuisine = RestaurantMenuCuisine.GENERAL
)

@Serializable
@JvmInline
value class OrderLineItemId(val value: String)

@Serializable
@JvmInline
value class OrderLineItemQuantity(val value: Int)

@Serializable
data class OrderLineItem(
    val id: OrderLineItemId,
    val quantity: OrderLineItemQuantity,
    val menuItemId: MenuItemId,
    val name: MenuItemName
)

@Serializable
enum class OrderStatus {
    CREATED, PREPARED, REJECTED, CANCELLED, PAYED
}

@Serializable
@JvmInline
value class PaymentId(@Serializable(with = UUIDSerializer::class) val value: UUID = UUID.randomUUID())

// COMMANDS

@Serializable
sealed class Command

@Serializable
sealed class RestaurantCommand : Command() {
    abstract val identifier: RestaurantId
}

@Serializable
sealed class OrderCommand : Command() {
    abstract val identifier: OrderId
}

@Serializable
sealed class PaymentCommand : Command() {
    abstract val identifier: PaymentId
}

@Serializable
data class CreateRestaurantCommand(
    override val identifier: RestaurantId = RestaurantId(),
    val name: RestaurantName,
    val menu: RestaurantMenu,
) : RestaurantCommand()

@Serializable
data class ChangeRestaurantMenuCommand(
    override val identifier: RestaurantId = RestaurantId(),
    val menu: RestaurantMenu,
) : RestaurantCommand()

@Serializable
data class PlaceOrderCommand(
    override val identifier: RestaurantId,
    val orderIdentifier: OrderId = OrderId(),
    @Serializable(with = ImmutableListSerializer::class)
    val lineItems: ImmutableList<OrderLineItem>,
) : RestaurantCommand()

@Serializable
data class CreateOrderCommand(
    override val identifier: OrderId,
    val restaurantIdentifier: RestaurantId = RestaurantId(),
    @Serializable(with = ImmutableListSerializer::class)
    val lineItems: ImmutableList<OrderLineItem>,
) : OrderCommand()

@Serializable
data class MarkOrderAsPreparedCommand(
    override val identifier: OrderId,
) : OrderCommand()

@Serializable
data class MarkOrderAsPayedCommand(
    override val identifier: OrderId,
) : OrderCommand()

@Serializable
data class PayCommand(
    override val identifier: PaymentId = PaymentId(),
    val orderId: OrderId,
) : PaymentCommand()

@Serializable
data class CancelPaymentCommand(
    override val identifier: PaymentId = PaymentId()
) : PaymentCommand()

// EVENTS

@Serializable
sealed class Event {
    abstract val final: Boolean
}

@Serializable
sealed class RestaurantEvent : Event() {
    abstract val identifier: RestaurantId
}

@Serializable
sealed class RestaurantErrorEvent : RestaurantEvent() {
    abstract val reason: Reason
}

@Serializable
sealed class OrderEvent : Event() {
    abstract val identifier: OrderId
}

@Serializable
sealed class OrderErrorEvent : OrderEvent() {
    abstract val reason: Reason
}

@Serializable
data class RestaurantCreatedEvent(
    override val identifier: RestaurantId,
    val name: RestaurantName,
    val menu: RestaurantMenu,
    override val final: Boolean = false,
) : RestaurantEvent()

@Serializable
data class RestaurantNotCreatedEvent(
    override val identifier: RestaurantId,
    val name: RestaurantName,
    val menu: RestaurantMenu,
    override val reason: Reason,
    override val final: Boolean = false,
) : RestaurantErrorEvent()

@Serializable
data class RestaurantMenuChangedEvent(
    override val identifier: RestaurantId,
    val menu: RestaurantMenu,
    override val final: Boolean = false,
) : RestaurantEvent()

@Serializable
data class RestaurantMenuNotChangedEvent(
    override val identifier: RestaurantId,
    val menu: RestaurantMenu,
    override val reason: Reason,
    override val final: Boolean = false,
) : RestaurantErrorEvent()

@Serializable
data class OrderPlacedAtRestaurantEvent(
    override val identifier: RestaurantId,
    @Serializable(with = ImmutableListSerializer::class)
    val lineItems: ImmutableList<OrderLineItem>,
    val orderId: OrderId,
    override val final: Boolean = false,
) : RestaurantEvent()

@Serializable
data class OrderNotPlacedAtRestaurantEvent(
    override val identifier: RestaurantId,
    @Serializable(with = ImmutableListSerializer::class)
    val lineItems: ImmutableList<OrderLineItem>,
    val orderId: OrderId,
    override val reason: Reason,
    override val final: Boolean = false,
) : RestaurantErrorEvent()

@Serializable
data class OrderRejectedByRestaurantEvent(
    override val identifier: RestaurantId,
    val orderId: OrderId,
    override val reason: Reason,
    override val final: Boolean = false,
) : RestaurantErrorEvent()

@Serializable
data class OrderCreatedEvent(
    override val identifier: OrderId,
    @Serializable(with = ImmutableListSerializer::class)
    val lineItems: ImmutableList<OrderLineItem>,
    val restaurantId: RestaurantId,
    override val final: Boolean = false,
) : OrderEvent() {
    val status: OrderStatus = OrderStatus.CREATED
}

@Serializable
data class OrderPreparedEvent(
    override val identifier: OrderId,
    override val final: Boolean = false,
) : OrderEvent() {
    val status: OrderStatus = OrderStatus.PREPARED
}

@Serializable
data class OrderPayedEvent(
    override val identifier: OrderId,
    override val final: Boolean = false,
) : OrderEvent() {
    val status: OrderStatus = OrderStatus.PAYED
}

@Serializable
data class OrderNotPreparedEvent(
    override val identifier: OrderId,
    override val reason: Reason,
    override val final: Boolean = false,
) : OrderErrorEvent()

@Serializable
data class OrderNotPayedEvent(
    override val identifier: OrderId,
    override val reason: Reason,
    override val final: Boolean = false,
) : OrderErrorEvent()

@Serializable
data class OrderRejectedEvent(
    override val identifier: OrderId,
    override val reason: Reason,
    override val final: Boolean = false,
) : OrderErrorEvent() {
    val status: OrderStatus = OrderStatus.REJECTED
}

