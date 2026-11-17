package com.aimodelhub.cache;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 检测 {@code @Cacheable} 方法缓存未命中的切面。
 * <p>
 * 当 {@code @Cacheable} 方法体真正被执行，说明缓存中没有目标 key，即未命中。
 * 本切面会按注解中声明的每个缓存名，通过
 * {@link CacheMetricsService#recordMiss(String)} 记录一次未命中。
 * <p>
 * 之所以可行，是因为 Spring 只有在缓存查不到值（返回 {@code null}）时
 * 才会真正调用目标方法。
 */
@Aspect
@Component
public class CacheMissAspect {

    private static final Logger log = LoggerFactory.getLogger(CacheMissAspect.class);

    private final CacheMetricsService metricsService;

    public CacheMissAspect(CacheMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * 环绕通知：方法体真正执行即说明缓存未命中，记录一次 miss 指标后继续执行。
     */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object recordMissOnCacheable(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 方法可能被代理，需要从原始方法上取注解
        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        if (cacheable == null) {
            // 注解也可能标在类上或被合成，再尝试从目标类上查找
            cacheable = joinPoint.getTarget().getClass()
                    .getMethod(method.getName(), method.getParameterTypes())
                    .getAnnotation(Cacheable.class);
        }

        if (cacheable != null) {
            for (String cacheName : cacheable.value()) {
                metricsService.recordMiss(cacheName);
            }
            log.debug("Cache miss recorded for {} on {}.{}",
                    cacheable.value(), method.getDeclaringClass().getSimpleName(), method.getName());
        }

        return joinPoint.proceed();
    }
}
