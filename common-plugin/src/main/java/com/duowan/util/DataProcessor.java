package com.duowan.util;

import com.duowan.annotation.EncryptData;
import com.google.common.base.CaseFormat;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: ce.liu
 * @Date: 2021/6/24 09:09
 */
@SupportedAnnotationTypes("com.duowan.annotation.EncryptData")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DataProcessor extends AbstractProcessor {

	/**
	 * 抽象语法树
	 */
	private JavacTrees trees;

	/**
	 * AST
	 */
	private TreeMaker treeMaker;

	/**
	 * 标识符
	 */
	private Names names;

	/**
	 * 日志处理
	 */
	private Messager messager;

	private Filer filer;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnvironment) {
		super.init(processingEnvironment);
		this.trees = JavacTrees.instance(processingEnv);
		Context context = ((JavacProcessingEnvironment)processingEnv).getContext();
		this.treeMaker = TreeMaker.instance(context);
		messager = processingEnvironment.getMessager();
		this.names = Names.instance(context);
		filer = processingEnvironment.getFiler();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		Set<? extends Element> annotation = roundEnv.getElementsAnnotatedWith(EncryptData.class);
		annotation.stream().map(element -> trees.getTree(element)).forEach(tree -> tree.accept(new TreeTranslator() {
			@Override
			public void visitClassDef(JCClassDecl jcClass) {
				//过滤属性
				Map<Name, JCVariableDecl> treeMap =
						jcClass.defs.stream().filter(k -> k.getKind().equals(Tree.Kind.VARIABLE))
								.map(tree -> (JCVariableDecl)tree)
								.collect(Collectors.toMap(JCVariableDecl::getName, Function.identity()));
				//处理变量
				treeMap.forEach((k, jcVariable) -> {
					messager.printMessage(Diagnostic.Kind.NOTE, String.format("fields:%s", k));
					try {
						JCModifiers modifiers = jcVariable.getModifiers();
						List<JCTree.JCAnnotation> annotations = modifiers.getAnnotations();
						if (annotations == null || annotations.size() <= 0) {
							return;
						}
						for (JCTree.JCAnnotation annotation : annotations) {
							JCTree.JCIdent jcIdent=(JCTree.JCIdent)annotation.getAnnotationType();
							if(jcIdent.name.contentEquals("EncryptField")&&
									jcVariable.getType().toString().equals("String")){
								deleteGetterMethod(jcClass,jcVariable);
								deleteSetterMethod(jcClass,jcVariable);
								//增加get方法
								//jcClass.defs = jcClass.defs.prepend(generateGetterMethod(jcClass,jcVariable));
								//增加set方法
								//jcClass.defs = jcClass.defs.prepend(generateSetterMethod(jcClass,jcVariable));
							}
						}
					} catch (Exception e) {
						messager.printMessage(Diagnostic.Kind.ERROR, "error");
					}
				});
				//增加toString方法
//				jcClass.defs = jcClass.defs.prepend(generateToStringBuilderMethod());
				super.visitClassDef(jcClass);
			}

			@Override
			public void visitMethodDef(JCMethodDecl jcMethod) {
				//打印所有方法
				messager.printMessage(Diagnostic.Kind.NOTE, jcMethod.toString());
				//修改方法
				if ("getTest".equals(jcMethod.getName().toString())) {
					result = treeMaker
							.MethodDef(jcMethod.getModifiers(), getNameFromString("testMethod"), jcMethod.restype,
									jcMethod.getTypeParameters(), jcMethod.getParameters(), jcMethod.getThrows(),
									jcMethod.getBody(), jcMethod.defaultValue);
				}
				super.visitMethodDef(jcMethod);
			}
		}));
		return true;
	}

	private void deleteGetterMethod(JCClassDecl jcClass, JCVariableDecl jcVariable){
		Name methodName = handleMethodSignature(jcVariable.getName(), "get");
		jcClass.defs.stream().forEach(e->{
			if(e.hasTag(JCTree.Tag.METHODDEF)){
				JCMethodDecl meth = (JCMethodDecl)e;
				System.out.println(meth.getName().toString());
				if (methodName.contentEquals(meth.getName())){
					String codes = ((JCMethodDecl) e).getBody().toString();
					System.out.println(codes);
				}
			}
		});
	}

	private JCMethodDecl generateGetterMethod(JCClassDecl jcClass, JCVariableDecl jcVariable) {

		//修改方法级别
		JCTree.JCModifiers jcModifiers = treeMaker.Modifiers(Flags.PUBLIC);

		//添加方法名称
		Name methodName = handleMethodSignature(jcVariable.getName(), "get");

		//添加方法内容
		ListBuffer<JCStatement> jcStatements = new ListBuffer<>();
		jcStatements.append(
				treeMaker.Return(treeMaker.Select(treeMaker.Ident(getNameFromString("this")), jcVariable.getName())));
		JCBlock jcBlock = treeMaker.Block(0, jcStatements.toList());

		//添加返回值类型
		JCExpression returnType = jcVariable.vartype;

		//参数类型
		List<JCTypeParameter> typeParameters = List.nil();

		//参数变量
		List<JCVariableDecl> parameters = List.nil();

		//声明异常
		List<JCExpression> throwsClauses = List.nil();
		//构建方法
		return treeMaker
				.MethodDef(jcModifiers, methodName, returnType, typeParameters, parameters, throwsClauses, jcBlock, null);
	}

	private void deleteSetterMethod(JCClassDecl jcClass,JCVariableDecl jcVariable){
		Name variableName = jcVariable.getName();
		Name methodName = handleMethodSignature(variableName, "set");

		jcClass.defs.stream().forEach(e->{
			if(e.hasTag(JCTree.Tag.METHODDEF)){
				JCMethodDecl meth = (JCMethodDecl)e;
				System.out.println(meth.getName().toString());
				if (methodName.contentEquals(meth.getName())){
					JCBlock jcBlock = ((JCMethodDecl) e).getBody();
					JCTree.JCStatement encryptCode = treeMaker.Exec(
							treeMaker.Apply(
									com.sun.tools.javac.util.List.nil(),
									treeMaker.Select(
											treeMaker.Ident(names.fromString("com.duowan.util.DataSecurityService")),
											names.fromString("aesEncrypt")),
									com.sun.tools.javac.util.List.of(treeMaker.Ident(variableName))));
					System.out.println(encryptCode);

					treeMaker.pos = meth.pos;
					meth.body = treeMaker.Block(0, List.of(
							treeMaker.Exec(
									treeMaker.Apply(
											com.sun.tools.javac.util.List.nil(),
											treeMaker.Select(
													treeMaker.Ident(names.fromString("com.duowan.util.DataSecurityService")),
													names.fromString("aesEncrypt")),
											com.sun.tools.javac.util.List.of(treeMaker.Ident(variableName)))),
							meth.body));
					String codes = ((JCMethodDecl) e).getBody().toString();
					System.out.println("codes is "+codes);
				}
			}
		});
	}

	private JCMethodDecl generateSetterMethod(JCClassDecl jcClass,JCVariableDecl jcVariable) throws ReflectiveOperationException {

		//修改方法级别
		JCModifiers modifiers = treeMaker.Modifiers(Flags.PUBLIC);

		//添加方法名称
		Name variableName = jcVariable.getName();
		Name methodName = handleMethodSignature(variableName, "set");

		//设置方法体
		ListBuffer<JCStatement> jcStatements = new ListBuffer<>();
		jcStatements.append(treeMaker.Exec(treeMaker
				.Assign(treeMaker.Select(treeMaker.Ident(getNameFromString("this")), variableName),
						treeMaker.Ident(variableName))));
		//定义方法体
		JCBlock jcBlock = treeMaker.Block(0, jcStatements.toList());

		//添加返回值类型
		JCExpression returnType =
				treeMaker.Type((Type)(Class.forName("com.sun.tools.javac.code.Type$JCVoidType").newInstance()));

		List<JCTypeParameter> typeParameters = List.nil();

		//定义参数
		JCVariableDecl variableDecl = treeMaker
				.VarDef(treeMaker.Modifiers(Flags.PARAMETER, List.nil()), jcVariable.name, jcVariable.vartype, null);
		List<JCVariableDecl> parameters = List.of(variableDecl);

		//声明异常
		List<JCExpression> throwsClauses = List.nil();
		return treeMaker
				.MethodDef(modifiers, methodName, returnType, typeParameters, parameters, throwsClauses, jcBlock, null);

	}

//	private JCMethodDecl generateToStringBuilderMethod() {
//
//		//修改方法级别
//		JCModifiers modifiers = treeMaker.Modifiers(Flags.PUBLIC);
//
//		//添加方法名称
//		Name methodName = getNameFromString("toString");
//
//		//设置调用方法函数类型和调用函数
//		JCExpressionStatement statement = treeMaker.Exec(treeMaker.Apply(List.of(memberAccess("java.lang.Object")),
//				memberAccess("com.nicky.lombok.adapter.AdapterFactory.builderStyleAdapter"),
//				List.of(treeMaker.Ident(getNameFromString("this")))));
//		ListBuffer<JCStatement> jcStatements = new ListBuffer<>();
//		jcStatements.append(treeMaker.Return(statement.getExpression()));
//		//设置方法体
//		JCBlock jcBlock = treeMaker.Block(0, jcStatements.toList());
//
//		//添加返回值类型
//		JCExpression returnType = memberAccess("java.lang.String");
//
//		//参数类型
//		List<JCTypeParameter> typeParameters = List.nil();
//
//		//参数变量
//		List<JCVariableDecl> parameters = List.nil();
//
//		//声明异常
//		List<JCExpression> throwsClauses = List.nil();
//
//		return treeMaker
//				.MethodDef(modifiers, methodName, returnType, typeParameters, parameters, throwsClauses, jcBlock, null);
//	}

	private JCExpression memberAccess(String components) {
		String[] componentArray = components.split("\\.");
		JCExpression expr = treeMaker.Ident(getNameFromString(componentArray[0]));
		for (int i = 1; i < componentArray.length; i++) {
			expr = treeMaker.Select(expr, getNameFromString(componentArray[i]));
		}
		return expr;
	}

	private Name handleMethodSignature(Name name, String prefix) {
		return names.fromString(prefix + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, name.toString()));
	}

	private Name getNameFromString(String s) {
		return names.fromString(s);
	}
}

