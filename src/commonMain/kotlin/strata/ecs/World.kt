package strata.ecs

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

typealias Entity = Int

class World {
    private val nextId = atomic(0)
    private val entities = mutableSetOf<Entity>()
    private val lock = ReentrantLock()

    fun create(): Entity {
        lock.withLock {
            val id = nextId.incrementAndGet()
            entities.add(id)
            return id
        }
    }

    fun destroy(entity: Entity) {
        lock.withLock { entities.remove(entity) }
    }

    fun entities(): Set<Entity> = lock.withLock { entities.toSet() }

    fun componentManager = ComponentManager()
}

class ComponentManager {
    private val stores = mutableMapOf<Class<*>, ComponentStore<*>>()

    inline fun <reified T : Any> register(): ComponentStore<T> {
        val cls = T::class.java as Class<*>
        return stores.getOrPut(cls) { ComponentStore<T>() } as ComponentStore<T>
    }

    inline fun <reified T : Any> getStore(): ComponentStore<T>? {
        return stores[T::class.java as Class<*>] as? ComponentStore<T>
    }

    inline fun <reified T : Any> hasStore(): Boolean {
        return stores.containsKey(T::class.java as Class<*>)
    }
}

class ComponentStore<T : Any> {
    private val components = mutableMapOf<Entity, T>()

    operator fun set(entity: Entity, component: T) {
        components[entity] = component
    }

    operator fun get(entity: Entity): T? = components[entity]

    fun remove(entity: Entity): T? = components.remove(entity)

    fun entries(): Map<Entity, T> = components.toMap()

    fun entities(): Set<Entity> = components.keys

    fun clear() = components.clear()
}
