package net.ancientloader.mixin;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mixin {
    /** Classes modified by this mixin. */
    Class<?>[] value() default {};

    /** String targets, useful when the target must not be loaded by the compiler. */
    String[] targets() default {};
}
