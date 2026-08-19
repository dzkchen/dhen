package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.data.price.BazaarSnapshot

class BazaarUpdateEvent internal constructor(val snapshot: BazaarSnapshot) : Event
