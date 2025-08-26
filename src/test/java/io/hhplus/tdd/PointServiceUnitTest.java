package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PointServiceUnitTest {

    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;
    private PointService pointService;

    @BeforeEach
    void setUp() {
        // Given
        userPointTable = mock(UserPointTable.class);
        pointHistoryTable = mock(PointHistoryTable.class);
        pointService = new PointService(userPointTable, pointHistoryTable);
        // Then: PointService가 정상 생성됨
    }

    // 정상 케이스 (충전)
    @Test
    @DisplayName("정상적으로 포인트를 충전하면 잔액이 증가한다")
    void givenValidAmount_whenCharge_thenBalanceIncreases() {
        // Given
        long userId = 1L;
        when(userPointTable.selectById(userId))
                .thenReturn(new UserPoint(userId, 0L, System.currentTimeMillis()));
        when(userPointTable.insertOrUpdate(userId, 100L))
                .thenReturn(new UserPoint(userId, 100L, System.currentTimeMillis()));

        // When
        UserPoint result = pointService.charge(userId, 100L);

        // Then
        assertThat(result.point()).isEqualTo(100L);
        verify(pointHistoryTable).insert(eq(userId), eq(100L), eq(TransactionType.CHARGE), anyLong());
    }

    // 정상 케이스 (사용)
    @Test
    @DisplayName("정상적으로 포인트를 사용하면 잔액이 차감된다")
    void givenEnoughBalance_whenUse_thenBalanceDecreases() {
        // Given
        long userId = 1L;
        when(userPointTable.selectById(userId))
                .thenReturn(new UserPoint(userId, 200L, System.currentTimeMillis()));
        when(userPointTable.insertOrUpdate(userId, 100L))
                .thenReturn(new UserPoint(userId, 100L, System.currentTimeMillis()));

        // When
        UserPoint result = pointService.use(userId, 100L);

        // Then
        assertThat(result.point()).isEqualTo(100L);
        verify(pointHistoryTable).insert(eq(userId), eq(100L), eq(TransactionType.USE), anyLong());
    }

    // 정상 케이스 (누적)
    @Test
    @DisplayName("동일 금액 반복 충전 시 잔액이 누적된다")
    void charge_repeatedSameAmount_accumulatesCorrectly() {
        long userId = 1L;
        long[] balance = {0L}; // <-- Mock 내부 상태

        // Given: selectById는 현재 balance를 반영
        when(userPointTable.selectById(userId))
                .thenAnswer(inv -> new UserPoint(userId, balance[0], System.currentTimeMillis()));

        // And: insertOrUpdate가 호출될 때 balance를 갱신
        when(userPointTable.insertOrUpdate(eq(userId), anyLong()))
                .thenAnswer(inv -> {
                    balance[0] = inv.getArgument(1, Long.class);
                    return new UserPoint(userId, balance[0], System.currentTimeMillis());
                });

        int repeats = 5;
        long amount = 100L;
        long expected = 0;

        // When & Then: 반복 충전 시 누적값 검증
        for (int i = 0; i < repeats; i++) {
            expected += amount;
            UserPoint updated = pointService.charge(userId, amount);
            assertThat(updated.point()).isEqualTo(expected);
        }

        // Then: 히스토리/저장 호출 횟수 검증
        verify(pointHistoryTable, times(repeats))
                .insert(eq(userId), eq(amount), eq(TransactionType.CHARGE), anyLong());
        verify(userPointTable, times(repeats)).insertOrUpdate(eq(userId), anyLong());
    }

    @Test
    @DisplayName("음수 금액 사용 시 400(IllegalArgumentException) 발생")
    void use_negativeAmount_throwsException() {
        // Given
        long userId = 1L;

        // When & Then
        assertThatThrownBy(() -> pointService.use(userId, -100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("금액은 0보다 큰 정수여야 합니다.");

        // Then: 저장 계층이 호출되지 않아야 함
        verifyNoInteractions(userPointTable, pointHistoryTable);
    }

    @Test
    @DisplayName("잔액 최대치 근처에서 사용 시 정상 차감된다")
    void use_nearMaxValue_succeeds() {
        // Given
        long userId = 1L;
        long nearMax = Long.MAX_VALUE - 10;
        when(userPointTable.selectById(userId))
                .thenReturn(new UserPoint(userId, nearMax, System.currentTimeMillis()));
        when(userPointTable.insertOrUpdate(userId, nearMax - 100))
                .thenReturn(new UserPoint(userId, nearMax - 100, System.currentTimeMillis()));

        // When
        UserPoint result = pointService.use(userId, 100L);

        // Then
        assertThat(result.point()).isEqualTo(nearMax - 100);
        verify(pointHistoryTable).insert(eq(userId), eq(100L), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("충전 시 오버플로우가 발생하면 예외를 던진다")
    void charge_overflow_throws() {
        // Given
        long userId = 1L;
        long almostMax = Long.MAX_VALUE - 5;
        when(userPointTable.selectById(userId))
                .thenReturn(new UserPoint(userId, almostMax, System.currentTimeMillis()));

        // When & Then
        assertThatThrownBy(() -> pointService.charge(userId, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("허용 범위를 초과합니다.");

        // Then: 저장 계층이 호출되지 않아야 함
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("잔액 부족 시 사용 실패")
    void use_insufficientBalance_throws() {
        // Given
        long userId = 1L;
        when(userPointTable.selectById(userId))
                .thenReturn(new UserPoint(userId, 50L, System.currentTimeMillis()));

        // When & Then
        assertThatThrownBy(() -> pointService.use(userId, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("잔액 부족");

        // Then
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("히스토리 조회: 빈 리스트면 그대로 빈 리스트 반환")
    void histories_emptyList_returnsEmpty() {
        // Given
        when(pointHistoryTable.selectAllByUserId(99L)).thenReturn(List.of());

        // When
        List<PointHistory> result = pointService.histories(99L);

        // Then
        assertThat(result).isEmpty();
        verify(pointHistoryTable).selectAllByUserId(99L);
    }

    @Test
    @DisplayName("없는 사용자 포인트 조회 시 null 그대로 전달")
    void get_nonExistingUser_returnsNull() {
        // Given
        when(userPointTable.selectById(123L)).thenReturn(null);

        // When
        UserPoint result = pointService.get(123L);

        // Then
        assertThat(result).isNull();
        verify(userPointTable).selectById(123L);
    }
}
