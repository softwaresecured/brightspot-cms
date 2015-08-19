package com.psddev.cms.view;

import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Recordable;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class ViewUtils {

    private ViewUtils() {
    }

    static final Logger LOGGER = LoggerFactory.getLogger(ViewUtils.class);

    // Returns classes in order of input class, followed by its super classes,
    // followed by its interfaces, followed by its modification classes.
    static List<Class<?>> getAnnotatableClasses(Class<?> klass) {

        List<Class<?>> classesToCheck = new ArrayList<>();

        classesToCheck.add(klass);

        // check super classes
        Class<?> superClass = klass.getSuperclass();

        while (superClass != null) {
            if (!Object.class.equals(superClass)) {
                classesToCheck.add(superClass);
            }

            superClass = superClass.getSuperclass();
        }

        // check interfaces
        classesToCheck.addAll(Arrays.asList(klass.getInterfaces()));

        // check modification classes
        if (Recordable.class.isAssignableFrom(klass)) {
            ObjectType type = ObjectType.getInstance(klass);
            if (type != null) {
                for (String modClassName : type.getModificationClassNames()) {
                    Class<?> modClass = ObjectUtils.getClassByName(modClassName);
                    if (modClass != null) {
                        classesToCheck.add(modClass);
                    }
                }
            }
        }

        return classesToCheck;
    }

    // TODO: Once https://github.com/perfectsense/dari/pull/105 is merged,
    // remove in favor of TypeDefinition#getInferredGenericTypeArgumentClass
    static Class<?> getGenericModelTypeArgument(Class<? extends ViewCreator> viewCreatorClass) {
        return getGenericTypeArgumentClass(viewCreatorClass, ViewCreator.class, 0);
    }

    // TODO: Once https://github.com/perfectsense/dari/pull/105 is merged,
    // remove in favor of TypeDefinition#getInferredGenericTypeArgumentClass
    static Class<?> getGenericViewTypeArgument(Class<? extends ViewCreator> viewCreatorClass) {
        return getGenericTypeArgumentClass(viewCreatorClass, ViewCreator.class, 1);
    }

    private static Class<?> getGenericTypeArgumentClass(Class<?> clazz, Class<?> baseClass, int baseClassGenericTypeArgumentIndex) {

        List<ClassInfo> hierarchy = getClassInfoHierarchy(clazz, baseClass);

        int argIndex = baseClassGenericTypeArgumentIndex;

        for (ClassInfo classInfo : hierarchy) {

            List<Type> superClassTypeVars = classInfo.getSuperClassTypeParameters();

            if (superClassTypeVars.size() > argIndex) {

                Type superClassTypeVar = superClassTypeVars.get(argIndex);

                if (superClassTypeVar instanceof Class) {
                    return (Class<?>) superClassTypeVar;

                } else if (superClassTypeVar instanceof TypeVariable) {

                    String superClassTypeVarName = ((TypeVariable) superClassTypeVar).getName();

                    int index = 0;
                    for (TypeVariable classTypeVar : classInfo.getClassTypeParameters()) {

                        if (superClassTypeVarName.equals(classTypeVar.getName())) {
                            argIndex = index;
                            break;
                        }
                        index++;
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return null;
    }

    private static List<ClassInfo> getClassInfoHierarchy(Class<?> clazz, Class<?> baseClass) {

        List<Class<?>> classes = getClassHierarchy(clazz, baseClass);

        List<Class<?>> classesReversed = new ArrayList<>(classes);
        Collections.reverse(classesReversed);
        classesReversed.add(baseClass);

        List<ClassInfo> hierarchy = new ArrayList<>();

        Class<?> prevClass = null;
        for (Class<?> nextClass : classesReversed) {

            if (prevClass != null) {
                Type superType = getGenericSuperType(prevClass, nextClass);

                if (superType != null) {
                    hierarchy.add(new ClassInfo(prevClass, superType));
                }
            }

            prevClass = nextClass;
        }

        Collections.reverse(hierarchy);

        return hierarchy;
    }

    private static List<Class<?>> getClassHierarchy(Class<?> clazz, Class<?> baseClass) {

        if (Object.class.equals(clazz)) {
            return null;

        } else if (baseClass.equals(clazz)) {
            return new ArrayList<>();
        }

        List<Class<?>> superTypes = new ArrayList<>();

        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            superTypes.add(superClass);
        }

        List<Class<?>> interfaceClasses = Arrays.asList(clazz.getInterfaces());
        superTypes.addAll(interfaceClasses);

        for (Class<?> superType : superTypes) {

            List<Class<?>> hierarchy = getClassHierarchy(superType, baseClass);

            if (hierarchy != null) {

                List<Class<?>> clazzes = new ArrayList<>();

                clazzes.addAll(hierarchy);
                clazzes.add(clazz);

                return clazzes;
            }
        }

        return null;
    }

    private static Type getGenericSuperType(Class<?> clazz, Class<?> superType) {

        if (superType.isInterface()) {
            for (Type genericInterface : clazz.getGenericInterfaces()) {

                if (genericInterface instanceof Class) {
                    if (superType.equals(genericInterface)) {
                        return genericInterface;
                    }

                } else if (genericInterface instanceof ParameterizedType) {

                    Type rawType = ((ParameterizedType) genericInterface).getRawType();
                    if (superType.equals(rawType)) {
                        return genericInterface;
                    }
                }
            }

        } else {
            return clazz.getGenericSuperclass();
        }

        return null;
    }

    private static final class ClassInfo {

        private Class<?> clazz;

        private Type genericSuperClazz;

        private ClassInfo(Class<?> clazz, Type genericSuperClazz) {
            this.clazz = clazz;
            this.genericSuperClazz = genericSuperClazz;
        }

        private List<TypeVariable> getClassTypeParameters() {
            return Arrays.asList(clazz.getTypeParameters());
        }

        private List<Type> getSuperClassTypeParameters() {
            if (genericSuperClazz instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericSuperClazz;
                return Arrays.asList(pt.getActualTypeArguments());
            } else {
                return new ArrayList<>();
            }
        }

        @Override
        public String toString() {

            List<TypeVariable> classTypeParameters = getClassTypeParameters();

            String classTypeParamsString = StringUtils.join(classTypeParameters.stream().map(Object::toString).collect(Collectors.toList()), ", ");
            if (!StringUtils.isBlank(classTypeParamsString)) {
                classTypeParamsString = "<" + classTypeParamsString + ">";
            }

            return clazz + classTypeParamsString + " <-- " + genericSuperClazz;
        }
    }
}
