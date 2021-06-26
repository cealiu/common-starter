package annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @Author: ce.liu
 * @Date: 2021/6/26 17:32
 */
@Documented
@Retention(RUNTIME)
@Target({FIELD,METHOD})
public @interface EncryptField {
}
