package com.docucloud.backend.audit.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {
    String action();
    String resourceType() default "";
    int resourceIdArgIndex() default -1; // -1 = no capturar resourceId
}
