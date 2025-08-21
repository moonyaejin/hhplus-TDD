package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/point")
public class PointController {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    // 유저별 직렬화를 위한 락 맵
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public PointController(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    // 특정 유저의 포인트 조회
    @GetMapping("/{id}")
    public UserPoint point(@PathVariable long id) {
        return userPointTable.selectById(id);
    }

    // 특정 유저의 포인트 충전 및 이용 내역 조회
    @GetMapping("/{id}/histories")
    public List<PointHistory> history(@PathVariable long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    // 문자열 본문을 long으로 안전 파싱
    private Long parseAmount(String body) {
        if (body == null) return null;
        try {
            return Long.parseLong(body.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean willOverflow(long a, long b) {
        return (b > 0 && a > Long.MAX_VALUE - b) || (b < 0 && a < Long.MIN_VALUE - b);
    }

    private MediaType textUtf8() {
        return new MediaType("text", "plain", StandardCharsets.UTF_8);
    }

    private ReentrantLock lockFor(long userId) {
        return locks.computeIfAbsent(userId, id -> new ReentrantLock());
    }

    private ResponseEntity<String> badAmount() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(textUtf8())
                .body("금액은 0보다 큰 정수여야 합니다.");
    }

    private ResponseEntity<String> overflow() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(textUtf8())
                .body("허용 범위를 초과합니다.");
    }

    // 특정 유저의 포인트 충전
    @RequestMapping(value = "/{id}/charge", method = RequestMethod.PATCH)
    public ResponseEntity<?> charge(@PathVariable long id, @RequestBody String body) {
        Long amount = parseAmount(body);
        if (amount == null || amount <= 0) return badAmount();

        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            UserPoint current = userPointTable.selectById(id);
            if (willOverflow(current.point(), amount)) return overflow();

            long newPoint = current.point() + amount;
            UserPoint updated = userPointTable.insertOrUpdate(id, newPoint);
            pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());
            return ResponseEntity.ok(updated);
        } finally {
            lock.unlock();
        }
    }

    // 특정 유저의 포인트 사용
    @RequestMapping(value = "/{id}/use", method = RequestMethod.PATCH)
    public ResponseEntity<?> use(@PathVariable long id, @RequestBody String body) {
        Long amount = parseAmount(body);
        if (amount == null || amount <= 0) return badAmount();

        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            UserPoint current = userPointTable.selectById(id);
            if (current.point() < amount) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .contentType(textUtf8())
                        .body("잔액 부족");
            }

            long newPoint = current.point() - amount;
            UserPoint updated = userPointTable.insertOrUpdate(id, newPoint);
            pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());
            return ResponseEntity.ok(updated);
        } finally {
            lock.unlock();
        }
    }
}
