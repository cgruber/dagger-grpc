package com.geekinasuit.daggergrpc.iogrpc.example.armeria.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.daggergrpc.api.GrpcApplicationComponent
import com.geekinasuit.daggergrpc.iogrpc.example.armeria.ExampleServer
import dagger.Component

@Component(modules = [ApplicationGraphModule::class, GrpcCallScopeGraph.BindingsModule::class])
@ApplicationScope
@GrpcApplicationComponent
interface ApplicationGraph {
  fun server(): ExampleServer

  @Component.Builder
  interface Builder {
    fun build(): ApplicationGraph
  }

  companion object {
    fun builder(): Builder = DaggerApplicationGraph.builder()
  }
}
