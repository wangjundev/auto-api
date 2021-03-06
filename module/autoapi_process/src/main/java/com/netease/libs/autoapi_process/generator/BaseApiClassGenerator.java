package com.netease.libs.autoapi_process.generator;

import com.netease.libs.autoapi.anno.AutoApiClassBuildMethodAnno;
import com.netease.libs.autoapi.anno.AutoApiConstructAnno;
import com.netease.libs.autoapi.anno.AutoApiMethodAnno;
import com.netease.libs.autoapi_process.AutoApiClass;
import com.netease.libs.autoapi_process.BaseClassGenerator;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

/**
 * Created by zyl06 on 2018/10/18.
 */

public abstract class BaseApiClassGenerator extends BaseClassGenerator {

    protected AutoApiClass mProviderClass;
    protected TypeElement mApiTarget;
    protected String mPkgName;
    private Set<String> mMethodSignatures = new HashSet<>();

    public BaseApiClassGenerator(AutoApiClass providerClass, String packageName) {
        super();
        mProviderClass = providerClass;
        mApiTarget = providerClass.clazz;
        mPkgName = packageName;
    }

    private boolean addSignature(ExecutableElement e) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(e.getSimpleName()).append("_");
        for (VariableElement ve : e.getParameters()) {
            sb.append(ClassName.get(ve.asType()).toString())
                    .append("_");
        }
        return mMethodSignatures.add(sb.toString());
    }

    protected void generate(TypeSpec.Builder builder) {
        for (Element e : mApiTarget.getEnclosedElements()) {
            if (!com.netease.libs.autoapi_process.ElementUtil.isPublic(e)) {
                continue;
            }
            if (e instanceof ExecutableElement) {
                AutoApiConstructAnno constructAnno = e.getAnnotation(AutoApiConstructAnno.class);
                if (constructAnno != null) {
                    String methodName = !constructAnno.alias().isEmpty() ? constructAnno.alias() : "newInstance";
                    addFactoryMethod(builder, (ExecutableElement) e, methodName);
                    continue;
                }

                AutoApiClassBuildMethodAnno classBuildAnno = e.getAnnotation(AutoApiClassBuildMethodAnno.class);
                if (classBuildAnno != null) {
                    if (com.netease.libs.autoapi_process.ElementUtil.isPublic(e) && com.netease.libs.autoapi_process.ElementUtil.isStatic(e)) {
                        String methodName = !classBuildAnno.alias().isEmpty() ? constructAnno.alias() : e.getSimpleName().toString();
                        addClassBuildMethod(builder, (ExecutableElement) e, methodName);
                    }
                    continue;
                }

                runExeElement(builder, (ExecutableElement) e);
            }
        }

        // 处理基类
        if (mProviderClass.includeSuperApi) {
            ClassName targetCn = ClassName.get(mApiTarget);
            TypeMirror tm = mApiTarget.getSuperclass();

            com.netease.libs.autoapi_process.Logger.w(targetCn + " superclass TypeMirror Class = " + tm.getClass());

            while (tm instanceof Type) {
                Symbol.TypeSymbol superElem = ((Type) tm).asElement();
                if (superElem == null) {
                    com.netease.libs.autoapi_process.Logger.w(targetCn + " is interface.");
                    break;
                }

                TypeName cn = ClassName.get(superElem.asType());

                com.netease.libs.autoapi_process.Logger.w(targetCn + " superclass = " + cn + "; super elem: " + superElem.getClass());

                if (cn.isPrimitive() || cn.equals(ClassName.OBJECT)) {
                    break;
                }

                for (Element e : superElem.getEnclosedElements()) {
                    if (com.netease.libs.autoapi_process.ElementUtil.isPublic(e) && e instanceof ExecutableElement) {
                        com.netease.libs.autoapi_process.Logger.w(targetCn + " ExecutableElement=" + e);

                        runExeElement(builder, (ExecutableElement) e);
                    }
                }

                boolean finish = true;
                if (superElem instanceof Symbol.ClassSymbol) {
                    tm = ((Symbol.ClassSymbol) superElem).getSuperclass();
                    finish = (tm == null || ClassName.get(tm).equals(ClassName.OBJECT));
                }
                if (finish) {
                    break;
                }
            }

            if (com.netease.libs.autoapi_process.ElementUtil.isInterface(mApiTarget)) {
                for (TypeMirror funcInter : mApiTarget.getInterfaces()) {
                    com.netease.libs.autoapi_process.Logger.w("funcInter = " + funcInter);
                    Element funcTypeElem = ((DeclaredType) funcInter).asElement();

                    for (Element e : funcTypeElem.getEnclosedElements()) {
                        if (e instanceof ExecutableElement && com.netease.libs.autoapi_process.ElementUtil.isPublic(e)) {
                            runExeElement(builder, (ExecutableElement) e);
                        }
                    }
                }
            }

        }
    }

    private void runExeElement(TypeSpec.Builder builder, ExecutableElement e) {
        if (!addSignature(e)) {
            return;
        }

        AutoApiMethodAnno anno = e.getAnnotation(AutoApiMethodAnno.class);
        if (anno != null && !anno.provide()) {
            return;
        }

        if (e.getKind() == ElementKind.CONSTRUCTOR) {
            return;
        }

        boolean provide = (anno != null && anno.provide()) ||
                (mProviderClass.allPublicStaticApi && com.netease.libs.autoapi_process.ElementUtil.isStatic(e)) ||
                (mProviderClass.allPublicNormalApi && !com.netease.libs.autoapi_process.ElementUtil.isStatic(e));
        if (provide) {
            String methodName = anno != null && !anno.alias().isEmpty() ?
                    anno.alias() : e.getSimpleName().toString();
            addMethod(builder, e, methodName);
        }
    }

    protected void addCustomFields(TypeSpec.Builder builder) {

    }

    protected void addMethod(TypeSpec.Builder builder, ExecutableElement e, String methodName) {

    }

    protected void addFactoryMethod(TypeSpec.Builder builder, ExecutableElement e, String methodName) {

    }

    protected void addClassBuildMethod(TypeSpec.Builder builder, ExecutableElement e, String methodName) {

    }

    protected String className(String providerNameSuffix, String anonymousSuffix) {
        if (mProviderClass.name != null && !mProviderClass.name.equals("")) {
            return providerNameSuffix != null ?
                    mProviderClass.name + providerNameSuffix :
                    mProviderClass.name;
        }

        List<String> enclosingNames = new LinkedList<>();
        Element enclosingElem = mApiTarget.getEnclosingElement();
        while (enclosingElem instanceof TypeElement) {
            enclosingNames.add(0, enclosingElem.getSimpleName().toString());
            enclosingElem = enclosingElem.getEnclosingElement();
        }

        StringBuilder sb = new StringBuilder();
        for (String enclosingName : enclosingNames) {
            sb.append(enclosingName).append("_");
        }
        sb.append(mApiTarget.getSimpleName());
        if (anonymousSuffix != null) {
            sb.append(anonymousSuffix);
        }

        return sb.toString();
    }

    /**
     *
     * @param e 具体真实 Element
     * @return 返回 对应 Api 类 TypeName 或元素自身的 TypeName
     */
    public TypeName tryConvertApiTypeName(Element e) {
        TypeName apiTypeName = com.netease.libs.autoapi_process.ElementUtil.getApiServiceClassName(e);
        return apiTypeName != null ? apiTypeName : ClassName.get(e.asType());
    }

    /**
     *
     * @param type 真实类型
     * @return 返回 对应 Api 类 TypeName 或元素自身的 TypeName
     */
    public TypeName tryConvertApiTypeName(TypeMirror type) {
        TypeName apiTypeName = com.netease.libs.autoapi_process.ElementUtil.getApiServiceClassName(type);
        return apiTypeName != null ? apiTypeName : ClassName.get(type);
    }

    protected void addTemplateTypes(MethodSpec.Builder builder, TypeMirror returnType) {
        com.netease.libs.autoapi_process.Logger.w("return " + returnType + "; class " + returnType.getClass());



        if (returnType instanceof Type.TypeVar) {
            Type.TypeVar typeVar = (Type.TypeVar) returnType;
            builder.addTypeVariable(TypeVariableName.get(typeVar));
        } else if (returnType instanceof Type.ClassType) {
            Type.ClassType classType = (Type.ClassType) returnType;
            List<Type> types = classType.getTypeArguments();
            for (Type type : types) {
                if (type instanceof TypeVariable) {
                    TypeVariable tv = (TypeVariable) type;
                    builder.addTypeVariable(TypeVariableName.get(tv));
                }
            }
        }
    }
}
