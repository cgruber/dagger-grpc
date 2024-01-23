package com.geekinasuit.daggergrpc.iogrpc.example.armeria.dagger

import com.geekinasuit.daggergrpc.api.GrpcCallContext
import com.geekinasuit.daggergrpc.api.GrpcCallScope
import javax.annotation.Generated
import com.geekinasuit.daggergrpc.iogrpc.example.armeria.HelloWorldService
import com.geekinasuit.daggergrpc.iogrpc.example.armeria.HelloWorldServiceAdapter
import com.geekinasuit.daggergrpc.iogrpc.example.armeria.WhateverService
import com.geekinasuit.daggergrpc.iogrpc.example.armeria.WhateverServiceAdapter
import dagger.Provides
import dagger.Subcomponent
import dagger.multibindings.IntoSet
import io.grpc.BindableService

@Generated
@Subcomponent(modules = [GrpcCallContext.Module::class])
@GrpcCallScope
interface GrpcCallScopeGraph {
  fun helloWorld(): HelloWorldService

  fun whatever(): WhateverService

  @Subcomponent.Builder
  interface Builder {
    fun build(): GrpcCallScopeGraph
  }

  /**
   * Supplies the [Subcomponent] that holds the grpc entry point handlers.
   *
   * This type must be inherited by your application-scoped dagger component, so that it is
   * avialable
   */
  interface Supplier {
    fun callScope(): GrpcCallScopeGraph
  }

  @dagger.Module(subcomponents = [GrpcCallScopeGraph::class])
  object BindingsModule {
    @Provides
    @IntoSet
    fun helloWorldHandler(graphBuilder: GrpcCallScopeGraph.Builder): BindableService =
      HelloWorldServiceAdapter { graphBuilder.build().helloWorld() }

    @Provides
    @IntoSet
    fun whateverHandler(graphBuilder: GrpcCallScopeGraph.Builder): BindableService =
      WhateverServiceAdapter { graphBuilder.build().whatever() }
  }
}

