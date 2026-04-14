package com.k8s.platform.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String resourceKind();
    String action();
    String clusterUidParam() default "clusterUid";
    String namespaceParam() default "namespace";
    String resourceNameParam() default "name";
}

