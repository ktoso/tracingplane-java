package edu.brown.cs.systems.tracingplane.baggage_buffers.compiler

import scala.collection.mutable.Set
import scala.collection.mutable.LinkedHashSet
import scala.collection.mutable.LinkedHashMap
import edu.brown.cs.systems.tracingplane.baggage_buffers.compiler.Ast._
import edu.brown.cs.systems.tracingplane.baggage_buffers.compiler.Ast.BuiltInType._

class JavaCompiler extends Compiler {
  
  val BagKey = "edu.brown.cs.systems.tracingplane.baggage_layer.BagKey"
  val ReaderHelpers = "edu.brown.cs.systems.tracingplane.baggage_buffers.impl.ReaderHelpers"
  val WriterHelpers = "edu.brown.cs.systems.tracingplane.baggage_buffers.impl.WriterHelpers"
  val Serializer = "edu.brown.cs.systems.tracingplane.baggage_layer.protocol.Serializer"
  val Parser = "edu.brown.cs.systems.tracingplane.baggage_layer.protocol.Parser"
  val BaggageReader = "edu.brown.cs.systems.tracingplane.baggage_layer.protocol.BaggageReader"
  val BaggageWriter = "edu.brown.cs.systems.tracingplane.baggage_layer.protocol.BaggageWriter"
  
  
  def compile(outputDir: String, bagDecl: BagDeclaration): Unit = {
    JavaCompilerUtils.writeOutputFile(outputDir, bagDecl.packageName, bagDecl.name, compile(bagDecl))
  }
  
  def compile(bagDecl: BagDeclaration): String = {
    val name = bagDecl.name
    val fields = bagDecl.fields.sortWith(_.index < _.index).map(getField(_))
    
    val fileContents = s"""/** Generated by BaggageBuffersCompiler */
    package ${bagDecl.packageName};

    public class $name {

        ${fields.map { _.fieldDeclaration }.mkString("\n")}
    
        public boolean _overflow = false;
    
        ${fields.map { _.bagKeyDeclaration }.mkString("\n")}
        
        public static final $Parser<$name> _parser = new ${name}Parser();
        private static class ${name}Parser implements $Parser<$name> {

            @Override
            public $name parse($BaggageReader reader) {
                $name instance = new $name();
                ${fields.map { _.parseStatement("instance", "reader") }.mkString}
                instance._overflow = reader.didOverflow();

                return instance;
            }
        }

        public static final $Serializer<$name> _serializer = new ${name}Serializer();
        private static class ${name}Serializer implements $Serializer<$name> {

            @Override
            public void serialize($BaggageWriter writer, $name instance) {
                if (instance == null) {
                    return;
                }

                writer.didOverflowHere(instance._overflow);
                ${fields.map { _.serializeStatement("instance", "writer") }.mkString }
            }
    
        }
    }

    """
        
    return JavaCompilerUtils.formatIndentation(fileContents, "    ");
  }
  
  def getField(decl: FieldDeclaration): FieldToCompile = {
    decl.fieldtype match {
      case p: PrimitiveType => return new PrimitiveFieldToCompile(decl.name, decl.index, p)
      case u: UserDefinedType => return new UserDefinedToCompile(decl.name, decl.index, u)
      case s: BuiltInType.Set => return new SetToCompile(decl.name, decl.index, s)
    }
  }
  
  sealed trait FieldToCompile {
    def fieldDeclaration(): String
    def bagKeyDeclaration(): String
    def serializeStatement(instance: String, builder: String): String
    def parseStatement(instance: String, reader: String): String
  }
  
  class PrimitiveFieldToCompile(name: String, index: Integer, fieldtype: PrimitiveType) extends FieldToCompile {
    
    val javaName = JavaCompilerUtils.formatCamelCase(name)
    val javaClass = getJavaType(fieldtype)
    
    val fieldDeclaration = s"public $javaClass $javaName = null;" 
    
    val bagKey = s"${javaName}Key"
    val bagKeyDeclaration = s"private static final $BagKey $bagKey = $BagKey.indexed(${index});"
    
    val cast = getCastFunction(fieldtype)
    
    def serializeStatement(instance: String, builder: String): String = {
      return s"""
        if ($instance.$javaName != null) {
            $builder.enter($bagKey);
            ${getJavaWriteMethod(builder, s"$instance.$javaName", fieldtype)};
            $builder.exit();
        }
      """
    }
    
    def parseStatement(instance: String, reader: String): String = {
      // TODO: add warnings / logging for multiple values
      return s"""
        if ($reader.enter($bagKey)) {
            $instance.$javaName = $ReaderHelpers.castNext($reader, $cast);
            $reader.exit();
        }
      """
    }
    
  }
  
  class UserDefinedToCompile(name: String, index: Integer, fieldtype: UserDefinedType) extends FieldToCompile {
    
    val javaName = JavaCompilerUtils.formatCamelCase(name)
    val javaClass = s"${fieldtype.packageName}.${JavaCompilerUtils.formatCamelCase(fieldtype.name)}"
    
    val fieldDeclaration = s"public $javaClass $javaName = null;" 
    
    val bagKey = s"${javaName}Key"
    val bagKeyDeclaration = s"private static final $BagKey $bagKey = $BagKey.indexed(${index});"
    
    def serializeStatement(instance: String, builder: String): String = {
      return s"""
        if ($instance.$javaName != null) {
            $builder.enter($bagKey);
            $javaClass._serializer.serialize($builder, $instance.$javaName);
            $builder.exit();
        }
      """
    }
    
    def parseStatement(instance: String, reader: String): String = {
      return s"""
        if ($reader.enter($bagKey)) {
            $instance.$javaName = $javaClass._parser.parse($reader);
            $reader.exit();
        }
      """
    }
    
  }
  
  class SetToCompile(name: String, index: Integer, fieldtype: BuiltInType.Set) extends FieldToCompile {
    
    var param: PrimitiveType = null
    fieldtype.of match {
      case t: PrimitiveType => param = t
      case _ => throw new CompileException("Unsupported type " + fieldtype + " for Set")
    }
    var paramJavaClass: String = getJavaPrimitiveType(param)
    
    val cast = getCastFunction(param)
    val supplier = s"() -> new java.util.HashSet<$paramJavaClass>()"
    
    val javaName = JavaCompilerUtils.formatCamelCase(name)
    val javaClass = s"java.util.Set<$paramJavaClass>"
    
    val fieldDeclaration = s"public $javaClass $javaName = null;" 
    
    val bagKey = s"${javaName}Key"
    val bagKeyDeclaration = s"private static final $BagKey $bagKey = $BagKey.indexed(${index});"
    
    def serializeStatement(instance: String, builder: String): String = {
      return s"""
        if ($instance.$javaName != null) {
            $builder.enter($bagKey);
            for ($paramJavaClass ${javaName}Instance : $instance.$javaName) {
                ${getJavaWriteMethod(builder, s"$instance.$javaName", param)};
            }
            $builder.sortData();
            $builder.exit();
        }
      """
    }
    
    def parseStatement(instance: String, reader: String): String = {
      return s"""
        if ($reader.enter($bagKey)) {
            $instance.$javaName = $ReaderHelpers.collect($reader, $cast, $supplier);
            $reader.exit();
        }
      """
    }
    
  }
  
  def getJavaType(fieldType: FieldType): String = {
    fieldType match {
      case prim: PrimitiveType => return getJavaPrimitiveType(prim)
      case BuiltInType.Set(of) => return s"java.util.Set<${getJavaType(of)}>"
      case UserDefinedType(packageName, name) => return s"$packageName.${JavaCompilerUtils.formatCamelCase(name)}"
      case _ => throw new CompileException(s"$fieldType not supported by JavaCompiler")
    }
  }
  
  def getJavaPrimitiveType(primitiveType: PrimitiveType): String = {
    primitiveType match {
      case BuiltInType.bool => return "Boolean"
      case BuiltInType.int32 => return "Integer"
      case BuiltInType.sint32 => return "Integer"
      case BuiltInType.fixed32 => return "Integer"
      case BuiltInType.sfixed32 => return "Integer"
      
      case BuiltInType.int64 => return "Long"
      case BuiltInType.sint64 => return "Long"
      case BuiltInType.fixed64 => return "Long"
      case BuiltInType.sfixed64 => return "Long"
      
      case BuiltInType.float => return "Float"
      case BuiltInType.double => return "Double"
      case BuiltInType.string => return "String"
      case BuiltInType.bytes => return "java.nio.ByteBuffer"
    }
  }
  
  def getJavaWriteMethod(writer: String, javaName: String, primitiveType: PrimitiveType): String = {
    primitiveType match {
      case BuiltInType.bool => return s"$WriterHelpers.writeBool($writer, $javaName)"
      case BuiltInType.int32 => return s"$WriterHelpers.writeUInt32($writer, $javaName)"
      case BuiltInType.sint32 => return s"$WriterHelpers.writeSInt32($writer, $javaName)"
      case BuiltInType.fixed32 => return s"$WriterHelpers.writeFixed32($writer, $javaName)"
      case BuiltInType.sfixed32 => return s"$WriterHelpers.writeFixed32($writer, $javaName)"
      
      case BuiltInType.int64 => return s"$WriterHelpers.writeUInt64($writer, $javaName)"
      case BuiltInType.sint64 => return s"$WriterHelpers.writeSInt64($writer, $javaName)"
      case BuiltInType.fixed64 => return s"$WriterHelpers.writeFixed64($writer, $javaName)"
      case BuiltInType.sfixed64 => return s"$WriterHelpers.writeFixed64($writer, $javaName)"
      
      case BuiltInType.float => return s"$WriterHelpers.writeFloat($writer, $javaName)"
      case BuiltInType.double => return s"$WriterHelpers.writeDouble($writer, $javaName)"
      case BuiltInType.string => return s"$WriterHelpers.writeString($writer, $javaName)"
      case BuiltInType.bytes => return s"$WriterHelpers.writeBytes($writer, $javaName)"
    }
  }
  
  def getCastFunction(primitiveType: PrimitiveType): String = {
    primitiveType match {
      case BuiltInType.bool => return s"$ReaderHelpers.ToBool"
      case BuiltInType.int32 => return s"$ReaderHelpers.ToUnsignedLexVarInt32"
      case BuiltInType.sint32 => return s"$ReaderHelpers.ToSignedLexVarInt32"
      case BuiltInType.fixed32 => return s"$ReaderHelpers.ToInt"
      case BuiltInType.sfixed32 => return s"$ReaderHelpers.ToInt"
      
      case BuiltInType.int64 => return s"$ReaderHelpers.ToUnsignedLexVarInt64"
      case BuiltInType.sint64 => return s"$ReaderHelpers.ToSignedLexVarInt64"
      case BuiltInType.fixed64 => return s"$ReaderHelpers.ToLong"
      case BuiltInType.sfixed64 => return s"$ReaderHelpers.ToFloat"
      
      case BuiltInType.float => return s"$ReaderHelpers.ToFloat"
      case BuiltInType.double => return s"$ReaderHelpers.ToDouble"
      case BuiltInType.string => return s"$ReaderHelpers.ToString"
      case BuiltInType.bytes => return s"$ReaderHelpers.ToBytes"
    }
  }
  
  
}