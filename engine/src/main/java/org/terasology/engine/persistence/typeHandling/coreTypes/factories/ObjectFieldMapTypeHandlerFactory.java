// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.engine.persistence.typeHandling.coreTypes.factories;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.persistence.typeHandling.TypeHandler;
import org.terasology.engine.persistence.typeHandling.TypeHandlerFactory;
import org.terasology.engine.persistence.typeHandling.TypeHandlerContext;
import org.terasology.engine.persistence.typeHandling.coreTypes.ObjectFieldMapTypeHandler;
import org.terasology.engine.persistence.typeHandling.coreTypes.RuntimeDelegatingTypeHandler;
import org.terasology.reflection.TypeInfo;
import org.terasology.engine.reflection.reflect.ConstructorLibrary;
import org.terasology.engine.utilities.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.Optional;

public class ObjectFieldMapTypeHandlerFactory implements TypeHandlerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectFieldMapTypeHandlerFactory.class);

    private final ConstructorLibrary constructorLibrary;

    public ObjectFieldMapTypeHandlerFactory(ConstructorLibrary constructorLibrary) {
        this.constructorLibrary = constructorLibrary;
    }

    @Override
    public <T> Optional<TypeHandler<T>> create(TypeInfo<T> typeInfo, TypeHandlerContext context) {
        Class<? super T> typeClass = typeInfo.getRawType();

        if (!Modifier.isAbstract(typeClass.getModifiers())
                && !typeClass.isLocalClass()
                && !(typeClass.isMemberClass() && !Modifier.isStatic(typeClass.getModifiers()))) {
            Map<Field, TypeHandler<?>> fieldTypeHandlerMap = Maps.newLinkedHashMap();

            getResolvedFields(typeInfo).forEach(
                    (field, fieldType) -> {
                        Optional<TypeHandler<?>> declaredFieldTypeHandler =
                                context.getTypeHandlerLibrary().getTypeHandler(fieldType);

                        TypeInfo<?> fieldTypeInfo = TypeInfo.of(fieldType);

                        fieldTypeHandlerMap.put(
                                field,
                                new RuntimeDelegatingTypeHandler(
                                        declaredFieldTypeHandler.orElse(null),
                                        fieldTypeInfo,
                                        context
                                )
                        );
                    }
            );

            ObjectFieldMapTypeHandler<T> mappedHandler =
                    new ObjectFieldMapTypeHandler<>(constructorLibrary.get(typeInfo), fieldTypeHandlerMap);

            return Optional.of(mappedHandler);
        }

        return Optional.empty();
    }

    private <T> Map<Field, Type> getResolvedFields(TypeInfo<T> typeInfo) {
        return AccessController.doPrivileged((PrivilegedAction<Map<Field, Type>>) () -> {
            Map<Field, Type> fields = Maps.newLinkedHashMap();

            Type type = typeInfo.getType();
            Class<? super T> rawType = typeInfo.getRawType();

            while (!Object.class.equals(rawType)) {
                for (Field field : rawType.getDeclaredFields()) {
                    if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }

                    field.setAccessible(true);
                    Type fieldType = ReflectionUtil.resolveType(type, field.getGenericType());
                    fields.put(field, fieldType);
                }

                rawType = rawType.getSuperclass();
            }

            return fields;
        });
    }
}
