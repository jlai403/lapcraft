package io.github.jlai403.lapcraft.app.catalog

import android.content.Context
import io.github.jlai403.lapcraft.core.session.SessionStore

/** Session store that resolves car codes to catalogue names when available. */
fun sessionStore(context: Context): SessionStore =
    SessionStore(context.filesDir, carName = CarCatalog(context)::name)