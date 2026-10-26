package com.quvantic.shop

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Product(val id: Int, val name: String, val price: BigDecimal)

data class CartLine(val productId: Int, val quantity: Int)

data class Cart(val id: String, val lines: List<CartLine>)

data class Order(val id: String, val cartId: String, val lines: List<CartLine>, val total: BigDecimal)

class ShopException(val status: Int, message: String) : RuntimeException(message)

/**
 * In-memory catalogue, carts and stock. Thread-safe: carts live in a concurrent map and stock
 * changes happen under one lock, so concurrent checkouts can never oversell.
 */
class Shop(productCount: Int = 50, initialStock: Int = 1_000_000) {
    val products: List<Product> = (1..productCount).map {
        Product(it, "Product $it", BigDecimal(it * 3 + 4).add(BigDecimal("0.99")))
    }
    private val byId = products.associateBy { it.id }
    private val stock = products.associate { it.id to initialStock }.toMutableMap()
    private val stockLock = Any()
    private val carts = ConcurrentHashMap<String, MutableList<CartLine>>()
    private val orders = ConcurrentHashMap<String, Order>()

    fun page(page: Int, size: Int): List<Product> {
        if (page < 1 || size !in 1..100) throw ShopException(400, "page must be >= 1 and size 1..100")
        return products.drop((page - 1) * size).take(size)
    }

    fun product(id: Int): Product = byId[id] ?: throw ShopException(404, "product $id not found")

    fun createCart(): Cart {
        val id = UUID.randomUUID().toString()
        carts[id] = mutableListOf()
        return Cart(id, emptyList())
    }

    fun cart(id: String): Cart {
        val lines = carts[id] ?: throw ShopException(404, "cart $id not found")
        return synchronized(lines) { Cart(id, lines.toList()) }
    }

    fun addItem(cartId: String, productId: Int, quantity: Int): Cart {
        if (quantity !in 1..10) throw ShopException(400, "quantity must be 1..10")
        product(productId)
        val lines = carts[cartId] ?: throw ShopException(404, "cart $cartId not found")
        synchronized(lines) { lines.add(CartLine(productId, quantity)) }
        return cart(cartId)
    }

    fun checkout(cartId: String): Order {
        val lines = carts.remove(cartId) ?: throw ShopException(404, "cart $cartId not found")
        val snapshot = synchronized(lines) { lines.toList() }
        if (snapshot.isEmpty()) {
            carts[cartId] = lines
            throw ShopException(409, "cart is empty")
        }
        val wanted = snapshot.groupBy { it.productId }.mapValues { (_, l) -> l.sumOf { it.quantity } }
        synchronized(stockLock) {
            val short = wanted.entries.firstOrNull { (id, qty) -> stock.getValue(id) < qty }
            if (short != null) {
                carts[cartId] = lines
                throw ShopException(409, "not enough stock for product ${short.key}")
            }
            wanted.forEach { (id, qty) -> stock[id] = stock.getValue(id) - qty }
        }
        val total = snapshot.fold(BigDecimal.ZERO) { acc, l -> acc + byId.getValue(l.productId).price * BigDecimal(l.quantity) }
        val order = Order(UUID.randomUUID().toString(), cartId, snapshot, total)
        orders[order.id] = order
        return order
    }

    fun order(id: String): Order = orders[id] ?: throw ShopException(404, "order $id not found")

    fun stockOf(productId: Int): Int = synchronized(stockLock) { stock[productId] ?: throw ShopException(404, "product $productId not found") }
}
