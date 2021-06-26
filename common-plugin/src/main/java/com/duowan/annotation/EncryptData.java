package com.duowan.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * @Author: ce.liu
 * @Date: 2021/6/24 14:26
 */
@Documented
@Retention(SOURCE)
@Target({TYPE,FIELD,METHOD})
public @interface EncryptData {

}
