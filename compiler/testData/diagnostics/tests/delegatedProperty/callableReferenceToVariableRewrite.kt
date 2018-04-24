// !DIAGNOSTICS: -UNUSED_PARAMETER

import kotlin.reflect.KProperty1

fun test() {
    filter {
        contains(it[Item::id])
    }
}

class Container {
    operator fun <R> get(prop: KProperty1<Item, R>): R = TODO()
}

class Entity
class Item(val id: Entity)

fun filter(predicate: (Container) -> Boolean) {}

fun contains(x: Entity): Boolean = true