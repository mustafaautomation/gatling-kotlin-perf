package com.quvantic.shop

import java.math.BigDecimal
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShopTest {
    @Test
    fun `pages through the catalogue`() {
        val shop = Shop(productCount = 25)
        assertEquals(10, shop.page(1, 10).size)
        assertEquals(5, shop.page(3, 10).size)
        assertTrue(shop.page(4, 10).isEmpty())
        assertEquals(400, assertFailsWith<ShopException> { shop.page(0, 10) }.status)
        assertEquals(400, assertFailsWith<ShopException> { shop.page(1, 101) }.status)
    }

    @Test
    fun `checkout totals lines with exact decimals and decrements stock`() {
        val shop = Shop(productCount = 3, initialStock = 10)
        val cart = shop.createCart()
        shop.addItem(cart.id, 1, 2) // 7.99 each
        shop.addItem(cart.id, 2, 1) // 10.99
        shop.addItem(cart.id, 1, 1)
        val order = shop.checkout(cart.id)
        assertEquals(BigDecimal("34.96"), order.total)
        assertEquals(7, shop.stockOf(1))
        assertEquals(9, shop.stockOf(2))
        assertEquals(order, shop.order(order.id))
    }

    @Test
    fun `a cart can only be checked out once`() {
        val shop = Shop()
        val cart = shop.createCart()
        shop.addItem(cart.id, 1, 1)
        shop.checkout(cart.id)
        assertEquals(404, assertFailsWith<ShopException> { shop.checkout(cart.id) }.status)
    }

    @Test
    fun `empty cart and short stock are conflicts and leave the cart usable`() {
        val shop = Shop(productCount = 1, initialStock = 2)
        val cart = shop.createCart()
        assertEquals(409, assertFailsWith<ShopException> { shop.checkout(cart.id) }.status)
        shop.addItem(cart.id, 1, 3)
        assertEquals(409, assertFailsWith<ShopException> { shop.checkout(cart.id) }.status)
        assertEquals(2, shop.stockOf(1))
        assertEquals(1, shop.cart(cart.id).lines.size)
    }

    @Test
    fun `rejects bad quantities and unknown ids`() {
        val shop = Shop()
        val cart = shop.createCart()
        assertEquals(400, assertFailsWith<ShopException> { shop.addItem(cart.id, 1, 0) }.status)
        assertEquals(400, assertFailsWith<ShopException> { shop.addItem(cart.id, 1, 11) }.status)
        assertEquals(404, assertFailsWith<ShopException> { shop.addItem(cart.id, 999, 1) }.status)
        assertEquals(404, assertFailsWith<ShopException> { shop.addItem("nope", 1, 1) }.status)
        assertEquals(404, assertFailsWith<ShopException> { shop.order("nope") }.status)
    }

    @Test
    fun `concurrent checkouts never oversell`() {
        val shop = Shop(productCount = 1, initialStock = 100)
        val pool = Executors.newFixedThreadPool(16)
        val sold = AtomicInteger()
        val rejected = AtomicInteger()
        repeat(300) {
            pool.submit {
                val cart = shop.createCart()
                shop.addItem(cart.id, 1, 1)
                try {
                    shop.checkout(cart.id)
                    sold.incrementAndGet()
                } catch (e: ShopException) {
                    if (e.status == 409) rejected.incrementAndGet()
                }
            }
        }
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        assertEquals(100, sold.get())
        assertEquals(200, rejected.get())
        assertEquals(0, shop.stockOf(1))
    }
}
