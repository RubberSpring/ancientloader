package net.ancientloader.mixin;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Inject {

    String[] method();

    At at();

    boolean cancellable() default false;

    int require() default 1;

    String id() default "";
}
