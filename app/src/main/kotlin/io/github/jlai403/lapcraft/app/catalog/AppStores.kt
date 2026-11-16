package io.github.jlai403.lapcraft.app.catalog

import android.content.Context
import io.github.jlai403.lapcraft.core.session.LapStore

fun lapStore(context: Context): LapStore =
    LapStore(context.filesDir, carName = CarCatalog(context)::name)