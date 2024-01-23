package com.geekinasuit.daggergrpc.wire.compile

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class GrpcServerDaggerProcessorProvider: SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
     GrpcServerDaggerProcessor(env = environment)

}
