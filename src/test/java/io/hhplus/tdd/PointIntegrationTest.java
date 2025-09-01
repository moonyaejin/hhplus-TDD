package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointController;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import io.hhplus.tdd.common.GlobalPointExceptionHandler;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(io.hhplus.tdd.common.GlobalPointExceptionHandler.class)
class PointIntegrationTest {

    MockMvc mockMvc;
    UserPointTable userPointTable;
    PointHistoryTable pointHistoryTable;

    @BeforeEach
    void setup() {
        // 매 테스트마다 DB 객체 생성
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();

        PointService pointService = new PointService(userPointTable, pointHistoryTable);
        PointController controller = new PointController(pointService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalPointExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("조회: 포인트가 없을 때 0 반환")
    void 포인트조회_없는유저_0원반환() throws Exception {
        mockMvc.perform(get("/point/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point", is(0)));
    }

    @Test
    @DisplayName("충전: 양수 금액 충전 시 정상 동작")
    void 포인트충전_성공() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point", is(1000)));

        mockMvc.perform(get("/point/1/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].amount", is(1000)))
                .andExpect(jsonPath("$[0].type", is("CHARGE")));
    }

    @Test
    @DisplayName("충전: 금액이 0이면 400 오류")
    void 포인트충전_금액0_실패() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string("금액은 0보다 큰 정수여야 합니다."));
    }

    @Test
    @DisplayName("충전: 금액이 음수면 400 오류")
    void 포인트충전_음수금액_실패() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("-100"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string("금액은 0보다 큰 정수여야 합니다."));
    }

    @Test
    @DisplayName("사용: 잔고 충분 → 정상 차감")
    void 포인트사용_성공() throws Exception {
        userPointTable.insertOrUpdate(1L, 1000);

        mockMvc.perform(patch("/point/1/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point", is(500)));

        mockMvc.perform(get("/point/1/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].amount", is(500)))
                .andExpect(jsonPath("$[0].type", is("USE")));
    }

    @Test
    @DisplayName("사용: 잔고 부족 → 409 오류")
    void 포인트사용_잔고부족_실패() throws Exception {
        userPointTable.insertOrUpdate(1L, 100);

        mockMvc.perform(patch("/point/1/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("200"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string("잔액 부족"));
    }

    @Test
    @DisplayName("내역 조회: 충전/사용 시 기록이 쌓이는지 확인")
    void 포인트내역_조회() throws Exception {
        userPointTable.insertOrUpdate(1L, 0);
        pointHistoryTable.insert(1L, 500, TransactionType.CHARGE, System.currentTimeMillis());
        pointHistoryTable.insert(1L, 200, TransactionType.USE, System.currentTimeMillis());

        mockMvc.perform(get("/point/1/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].type", is("CHARGE")))
                .andExpect(jsonPath("$[1].type", is("USE")));
    }
}
