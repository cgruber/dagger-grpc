package com.geekinasuit.daggergrpc.wire.compile

import com.geekinasuit.daggergrpc.api.GrpcServiceHandler
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import io.grpc.BindableService
import io.grpc.ServerServiceDefinition

class GrpcServerDaggerProcessor(val env: SymbolProcessorEnvironment) : SymbolProcessor {
  private val log
    get() = env.logger

  private val generator
    get() = env.codeGenerator

  override fun process(resolver: Resolver): List<KSAnnotated> {
    log.info("Processing @GrpcServiceHandler types.")
    val annotatedTypes =
      resolver.getSymbolsWithAnnotation(GrpcServiceHandler::class.qualifiedName!!)

    val files = mutableListOf<FileSpec?>()
    annotatedTypes.map { createAdapter(resolver, it) }.forEach { (type, file) -> files.add(file) }
    files.filterNotNull().forEach { it.writeTo(generator, Dependencies.ALL_FILES) }
    return listOf()
  }

  private fun createAdapter(resolver: Resolver, type: KSAnnotated): Pair<KSAnnotated, FileSpec?> {
    val info = type.accept(ClassInfo.Visitor, resolver)
    val annotation =
      type.annotations.first { it.shortName.getShortName() == GrpcServiceHandler::class.simpleName }
    val grpcWrapperType =
      annotation.arguments.first { it.name?.getShortName() == "grpcWrapperType" }.value as KSType
    val grpcWrapperTypeName = grpcWrapperType.toClassName()
    log.info("GrpcWrapperType: $grpcWrapperType")
    val asyncService =
      resolver.getClassDeclarationByName("${grpcWrapperTypeName.canonicalName}.AsyncService")
    if (asyncService == null) {
      log.error(
        "Could not find ${grpcWrapperTypeName.canonicalName}.AsyncService on the classpath."
      )
      return type to null
    }
    if (!info.superTypes.map { it.name }.contains(asyncService.toClassName())) {
      log.error("$type must implement ${asyncService.toClassName()}")
      return type to null
    }

    val className = "${info.name.simpleName}Adapter"
    val serviceBits = "service" to LambdaTypeName.get(returnType = asyncService.toClassName())
    val serviceProperty =
      PropertySpec.builder(serviceBits.first, serviceBits.second)
        .addModifiers(KModifier.PRIVATE)
        .initializer(serviceBits.first)
        .build()
    val builder =
      TypeSpec.classBuilder(className)
        .addKdoc("An adapter to bind [AsyncService] to the gRPC subsystem.")
        .addSuperinterface(BindableService::class)
        .addSuperinterface(asyncService.toClassName())
        .addProperty(serviceProperty)
        .primaryConstructor(
          FunSpec.constructorBuilder().addParameter(serviceBits.first, serviceBits.second).build()
        )
        .addFunction(
          FunSpec.builder("bindService")
            .addModifiers(KModifier.OVERRIDE)
            .returns(ServerServiceDefinition::class)
            .addCode("return %L.bindService(this)", grpcWrapperTypeName)
            .build()
        )
    asyncService.getDeclaredFunctions().forEach {
      val (requestParameterDeclaration, responseParameterDeclaration) = it.parameters
      val requestParameter =
        ParameterSpec.builder(
            requestParameterDeclaration.name!!.getShortName(),
            requestParameterDeclaration.type.toTypeName()
          )
          .build()
      val responseParameter =
        with(responseParameterDeclaration) {
          ParameterSpec.builder(name!!.getShortName(), this.type.toTypeName()).build()
        }
      builder.addFunction(
        FunSpec.builder(it.simpleName.getShortName())
          .addParameter(requestParameter)
          .addParameter(responseParameter)
          .addModifiers(KModifier.OVERRIDE)
          .addCode("return service().%L(request, responseObserver)", it.simpleName.getShortName())
          .build()
      )
    }

    log.info("Generating ${info.name.packageName}.$className")
    val file =
      FileSpec.builder(info.name.packageName, className)
        .addType(builder.build())
        .build()
    log.info(file.toString())
    return type to file
  }
}
/*
class HelloWorldServiceAdapter(val service: () -> AsyncService) : BindableService, AsyncService {
  override fun sayHello(
    request: HelloWorld.SayHelloRequest,
    responseObserver: StreamObserver<HelloWorld.SayHelloResponse>
  ) = service().sayHello(request, responseObserver)

  override fun sayGoodbye(
    request: HelloWorld.SayGoodbyeRequest,
    responseObserver: StreamObserver<HelloWorld.SayGoodbyeResponse>
  ) = service().sayGoodbye(request, responseObserver)

  override fun bindService(): ServerServiceDefinition = HelloWorldServiceGrpc.bindService(this)
}


 */
