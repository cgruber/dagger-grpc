package com.geekinasuit.daggergrpc.wire.compile

import com.geekinasuit.daggergrpc.api.GrpcApplicationComponent
import com.geekinasuit.daggergrpc.api.GrpcServiceHandler
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.ksp.writeTo

class GrpcServerDaggerProcessor(val env: SymbolProcessorEnvironment) : SymbolProcessor {
  private val log
    get() = env.logger

  private val generator
    get() = env.codeGenerator

  override fun process(resolver: Resolver): List<KSAnnotated> {
    log.info("Processing @GrpcServiceHandler types.")
    val handlers =
      resolver.getSymbolsWithAnnotation(GrpcServiceHandler::class.qualifiedName!!)
        .toList()
    val applicationGraph =
      resolver
        .getSymbolsWithAnnotation(GrpcApplicationComponent::class.qualifiedName!!)
        .firstOrNull()

    if (applicationGraph != null) {
      if (handlers.isEmpty()) {
        log.error("No grpc handlers (classes annotated with @GrpcServiceHandler) found.")
      } else {
        val files =
          handlers.map { AdapterClassGenerator(log, resolver, it).generate() }.toMutableList()
        files.add(GrpcCallScopeGraphGenerator(log, resolver, applicationGraph, handlers).generate())
        files.filterNotNull().forEach { it.writeTo(generator, Dependencies.ALL_FILES) }
      }
    }
    return listOf()
  }
}
