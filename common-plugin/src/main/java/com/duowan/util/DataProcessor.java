package com.duowan.util;

import com.duowan.annotation.EncryptData;
import com.google.common.base.CaseFormat;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
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
//								modifyGetterMethod(jcClass,jcVariable);
								modifySetterMethod(jcClass,jcVariable);
							}
						}
					} catch (Exception e) {
						messager.printMessage(Diagnostic.Kind.ERROR, "error");
					}
				});
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
		return false;
	}

	private void modifyGetterMethod(JCClassDecl jcClass, JCVariableDecl jcVariable){
		Name variableName = jcVariable.getName();
		Name methodName = handleMethodSignature(variableName, "get");
		jcClass.defs.stream().forEach(e->{
			if(e.hasTag(JCTree.Tag.METHODDEF)){
				JCMethodDecl meth = (JCMethodDecl)e;
				if (methodName.contentEquals(meth.getName())){
					JCTree tree = e.getTree();
					tree.accept(new TreeTranslator() {
						@Override
						public void visitBlock(JCTree.JCBlock tree) {
							ListBuffer<JCTree.JCStatement> statements = new ListBuffer();
							JCTree.JCFieldAccess fieldAccess = treeMaker.Select(treeMaker.Select(treeMaker.Select(treeMaker.Select(treeMaker.Ident(names.fromString("com")),names.fromString("duowan")), names.fromString("util")), names.fromString("DataSecurityService")), names.fromString("aesDecrypt"));
							JCTree.JCExpression argsExpr = treeMaker.Ident(variableName);
							JCTree.JCMethodInvocation methodInvocation = treeMaker.Apply(List.nil(), fieldAccess, List.of(argsExpr));
							JCTree.JCReturn code = treeMaker.Return(methodInvocation);
							statements.append(code);
							result = treeMaker.Block(0, statements.toList());
						}
					});
				}
			}
		});
	}

	private void modifySetterMethod(JCClassDecl jcClass,JCVariableDecl jcVariable){
		Name variableName = jcVariable.getName();
		Name methodName = handleMethodSignature(variableName, "set");

		jcClass.defs.stream().forEach(e->{
			if(e.hasTag(JCTree.Tag.METHODDEF)){
				JCMethodDecl meth = (JCMethodDecl)e;
				if (methodName.contentEquals(meth.getName())){
					JCTree tree = e.getTree();
					tree.accept(new TreeTranslator() {
						@Override
						public void visitBlock(JCTree.JCBlock tree) {
							ListBuffer<JCTree.JCStatement> statements = new ListBuffer();
							JCTree.JCFieldAccess fieldAccess = treeMaker.Select(treeMaker.Select(treeMaker.Select(treeMaker.Select(treeMaker.Ident(names.fromString("com")),names.fromString("duowan")), names.fromString("util")), names.fromString("DataSecurityService")), names.fromString("aesEncrypt"));
							JCTree.JCExpression argsExpr = treeMaker.Ident(variableName);
							JCTree.JCMethodInvocation methodInvocation = treeMaker.Apply(List.nil(), fieldAccess, List.of(argsExpr));
							JCTree.JCExpressionStatement code = treeMaker.Exec(	treeMaker.Assign(treeMaker.Select(treeMaker.Ident(getNameFromString("this")), variableName),
									methodInvocation));
							statements.append(code);
							result = treeMaker.Block(0, statements.toList());
						}
					});
				}
			}
		});
	}

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

