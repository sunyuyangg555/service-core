package org.sunyuyangg.service.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionMapping {

    String name() default "";

    int maxArgs() default 0;

    boolean caseSensitive() default false;

    Option[] options();

    OptionGroup[] optionGroup() ;

    OptionNeeds[] optionNeeds();

    String desc() default "";
 }
