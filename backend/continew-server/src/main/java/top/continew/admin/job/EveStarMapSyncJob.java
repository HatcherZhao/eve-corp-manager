package top.continew.admin.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.service.EveStarMapService;

/**
 * 保守续跑国服公开宇宙星图索引，所有单次请求仍由全局 ESI 节流器控制。
 *
 * @author zhaoyuqing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EveStarMapSyncJob {

    private static final String LOCK_NAME = "eve:serenity:starmap-sync";

    private final RedissonClient redissonClient;
    private final EveStarMapService starMapService;

    /** 持续续跑少量星系；实际国服请求速率由共享节流器严格控制。 */
    @Scheduled(initialDelayString = "${eve.starmap.initial-delay:PT45S}", fixedDelayString = "${eve.starmap.sync-interval:PT5S}")
    public void synchronize() {
        RLock lock = redissonClient.getLock(LOCK_NAME);
        if (!lock.tryLock()) {
            return;
        }
        try {
            starMapService.synchronizeNextBatch();
        } catch (RuntimeException e) {
            log.warn("EVE 公开星图同步失败，errorType={}", e.getClass().getSimpleName());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
