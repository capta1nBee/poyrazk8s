package com.k8s.platform.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify required permission for a controller method
 * This is used in addition to the automatic permission checking
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * Resource kind (e.g., "Pod", "Deployment")
     */
    String kind();

    /**
     * Required action (e.g., "get", "list", "create", "delete", "scale")
     */
    String action();

    /**
     * Whether to check namespace-level permission
     */
    boolean checkNamespace() default true;
}
