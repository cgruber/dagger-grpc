package com.geekinasuit.daggergrpc.wire.compile

import com.geekinasuit.daggergrpc.api.GrpcCallContext
import com.geekinasuit.daggergrpc.api.GrpcCallScope
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.multibindings.IntoSet
import io.grpc.BindableService

private const val CLASS_NAME = "GrpcCallScopeGraph"

class GrpcCallScopeGraphGenerator(
  private val log: KSPLogger,
  private val resolver: Resolver,
  private val applicationGraphType: KSAnnotated,
  private val handlerTypes: List<KSAnnotated>
) {
  private val appComponent by lazy { applicationGraphType.accept(ClassInfo.Visitor, resolver) }

  private val handlers by lazy { handlerTypes.map { it.accept(ClassInfo.Visitor, resolver) } }

  fun generate(): FileSpec {
    log.info("Generating ${appComponent.name.packageName}.$CLASS_NAME")
    val className = ClassName.bestGuess("${appComponent.name.packageName}.$CLASS_NAME")
    val builder = prepareBuilder(className)
    return FileSpec.builder(appComponent.name.packageName, CLASS_NAME)
      .addType(builder.build())
      .build()
      .also { log.info(it.toString()) }
  }

  private fun prepareBuilder(className: ClassName): TypeSpec.Builder {
    val subcomponentAnnotation =
      AnnotationSpec.builder(Subcomponent::class)
        .addMember(
          "modules = [%T::class]",
          ClassName.bestGuess(GrpcCallContext.Module::class.qualifiedName!!)
        )

    val subcomponent =
      TypeSpec.interfaceBuilder(className)
        .addAnnotation(subcomponentAnnotation.build())
        .addAnnotation(GrpcCallScope::class)
        .addType(
          TypeSpec.interfaceBuilder("Builder")
            .addAnnotation(Subcomponent.Builder::class)
            .addFunction(
              FunSpec.builder("build")
                .addModifiers(KModifier.ABSTRACT)
                .returns(ClassName.bestGuess(CLASS_NAME))
                .build()
            )
            .build()
        )
    val bindingsModule =
      TypeSpec.objectBuilder("GrpcBindingsModule")
        .addAnnotation(
          AnnotationSpec.builder(Module::class)
            .addMember("subcomponents = [%L::class]", CLASS_NAME)
            .build()
        )

    handlers.forEach { handler ->
      subcomponent.addFunction(
        FunSpec.builder(handler.name.simpleName.lowerFirst())
          .addModifiers(KModifier.ABSTRACT)
          .returns(handler.name)
          .build()
      )

      bindingsModule.addFunction(
        FunSpec.builder(handler.name.simpleName.lowerFirst())
          .addAnnotation(Provides::class)
          .addAnnotation(IntoSet::class)
          .addParameter("graphBuilder", ClassName.bestGuess("Builder"))
          .returns(BindableService::class)
          .addCode(
            "return %LAdapter { graphBuilder.build().%L() }",
            handler.name,
            handler.name.simpleName.lowerFirst()
          )
          .build()
      )
    }
    subcomponent.addType(bindingsModule.build())
    return subcomponent
  }
}

fun String.lowerFirst() = this[0].lowercase() + this.substring(1)
/*

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

  @dagger.Module(subcomponents = [GrpcCallScopeGraph::class])
  object BindingsModule {
    @Provides
    @IntoSet
    fun helloWorldHandler(graphBuilder: Builder): BindableService =
      HelloWorldServiceAdapter { graphBuilder.build().helloWorld() }

    @Provides
    @IntoSet
    fun whateverHandler(graphBuilder: Builder): BindableService =
      WhateverServiceAdapter { graphBuilder.build().whatever() }
  }
}
 */
