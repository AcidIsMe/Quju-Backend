package com.quju.platform.component.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Aspect
@Component
public class RedisLockAspect {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = distributedLock.key();
        // Resolve SpEL-like key placeholders from method arguments
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0 && lockKey.contains("#")) {
            for (int i = 0; i < args.length; i++) {
                lockKey = lockKey.replace("#" + i, String.valueOf(args[i]));
            }
        }
        ReentrantLock lock = locks.computeIfAbsent(lockKey, k -> new ReentrantLock());
        lock.lock();
        try {
            return joinPoint.proceed();
        } finally {
            lock.unlock();
        }
    }
}
