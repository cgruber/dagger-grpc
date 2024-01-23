package com.geekinasuit.daggergrpc.wire.compile

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getKotlinClassByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName

data class ClassInfo(val name: ClassName, val superTypes: List<ClassInfo>) {

  object Visitor : KSDefaultVisitor<Resolver, ClassInfo>() {
    @OptIn(KspExperimental::class)
    override fun visitClassDeclaration(decl: KSClassDeclaration, resolver: Resolver): ClassInfo {
      return ClassInfo(
        name = decl.toClassName(),
        superTypes = decl.superTypes.map {
          val type: KSType = it.resolve()
          val superDecl = resolver.getKotlinClassByName(type.toClassName().canonicalName)!!
          visitClassDeclaration(superDecl, resolver)
        }.toList()
      )
    }

    override fun defaultHandler(node: KSNode, data: Resolver): ClassInfo {
      throw IllegalArgumentException("Cannot use ${this.javaClass} on non-Type objects.")
    }
  }
}
