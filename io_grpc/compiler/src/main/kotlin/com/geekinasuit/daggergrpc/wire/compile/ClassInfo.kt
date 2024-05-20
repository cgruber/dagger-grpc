package com.geekinasuit.daggergrpc.wire.compile

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getKotlinClassByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName

data class ClassInfo(
  val name: ClassName,
  val superTypes: List<ClassInfo>,
  val annotations: List<KSAnnotation>
) {
  object Visitor : KSDefaultVisitor<Resolver, ClassInfo>() {
    @OptIn(KspExperimental::class)
    override fun visitClassDeclaration(
      classDeclaration: KSClassDeclaration,
      data: Resolver
    ): ClassInfo {
      return ClassInfo(
        name = classDeclaration.toClassName(),
        annotations = classDeclaration.annotations.toList(),
        superTypes =
          classDeclaration.superTypes
            .map {
              val type: KSType = it.resolve()
              val superDecl = data.getKotlinClassByName(type.toClassName().canonicalName)!!
              visitClassDeclaration(superDecl, data)
            }
            .toList(),
      )
    }

    override fun defaultHandler(node: KSNode, data: Resolver): ClassInfo {
      throw IllegalArgumentException("Cannot use ${this.javaClass} on non-Type objects.")
    }
  }
}
