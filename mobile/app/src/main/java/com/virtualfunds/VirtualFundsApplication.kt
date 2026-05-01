package com.virtualfunds

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// @HiltAndroidApp is the entry point that bootstraps the entire Hilt DI graph.
// It triggers code generation of a top-level "application component" that knows
// how to satisfy every @Inject-annotated constructor and every @HiltViewModel.
// Without this annotation on the Application class, no injection anywhere in the
// app will work — Hilt simply won't initialise.
@HiltAndroidApp
class VirtualFundsApplication : Application()
