package com.geekinasuit.daggergrpc.wire.compile

import com.geekinasuit.daggergrpc.api.GrpcServiceHandler
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
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
import io.grpc.BindableService
import io.grpc.ServerServiceDefinition

class AdapterClassGenerator(
  private val log: KSPLogger,
  private val resolver: Resolver,
  private val handlerType: KSAnnotated,
) {

  private val info by lazy { handlerType.accept(ClassInfo.Visitor, resolver) }

  private val className by lazy { "${info.name.simpleName}Adapter" }

  private val grpcWrapperType by lazy {
    val annotation =
      info.annotations.first { it.shortName.getShortName() == GrpcServiceHandler::class.simpleName }
    annotation.arguments.first { it.name?.getShortName() == "grpcWrapperType" }.value as KSType
  }

  private val asyncService by lazy {
    val grpcWrapperName = grpcWrapperType.toClassName().canonicalName
    val asyncService = resolver.getClassDeclarationByName("$grpcWrapperName.AsyncService")
    asyncService.also {
      when {
        asyncService == null -> {
          log.error("Could not find $grpcWrapperName.AsyncService on the classpath.")
        }
        !info.superTypes.map { it.name }.contains(asyncService.toClassName()) -> {
          log.error("$handlerType must implement ${asyncService.toClassName()}")
        }
      }
    }
  }

  internal fun generate(): FileSpec? {
    val svc = asyncService ?: return null
    val builder = prepareAdapterBuilder(className, svc)
    svc.getDeclaredFunctions().forEach { buildDelegatingFunctions(it, builder) }
    log.info("Generating ${info.name.packageName}.$className")
    val file = FileSpec.builder(info.name.packageName, className).addType(builder.build()).build()
    log.info(file.toString())
    return file
  }

  private fun prepareAdapterBuilder(
    className: String,
    asyncService: KSClassDeclaration,
  ): TypeSpec.Builder {
    val serviceProperty =
      PropertySpec.builder("service", LambdaTypeName.get(returnType = asyncService.toClassName()))
        .addModifiers(KModifier.PRIVATE)
        .initializer("service")
        .build()
    return TypeSpec.classBuilder(className)
      .addKdoc("An adapter to bind [AsyncService] to the gRPC subsystem.")
      .addSuperinterface(BindableService::class)
      .addSuperinterface(asyncService.toClassName())
      .addProperty(serviceProperty)
      .primaryConstructor(
        FunSpec.constructorBuilder()
          .addParameter(serviceProperty.name, serviceProperty.type)
          .build(),
      )
      .addFunction(
        FunSpec.builder("bindService")
          .addModifiers(KModifier.OVERRIDE)
          .returns(ServerServiceDefinition::class)
          .addCode("return %L.bindService(this)", grpcWrapperType.toClassName())
          .build(),
      )
  }

  private fun buildDelegatingFunctions(it: KSFunctionDeclaration, builder: TypeSpec.Builder) {
    val (req, resp) = it.parameters
    val requestParameter =
      ParameterSpec.builder(req.name!!.getShortName(), req.type.toTypeName()).build()
    val responseParameter =
      ParameterSpec.builder(resp.name!!.getShortName(), resp.type.toTypeName()).build()
    builder.addFunction(
      FunSpec.builder(it.simpleName.getShortName())
        .addParameter(requestParameter)
        .addParameter(responseParameter)
        .addModifiers(KModifier.OVERRIDE)
        .addCode("return service().%L(request, responseObserver)", it.simpleName.getShortName())
        .build(),
    )
  }
}
