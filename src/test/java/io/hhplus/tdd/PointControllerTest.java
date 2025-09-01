package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.GlobalPointExceptionHandler;
import io.hhplus.tdd.point.PointController;
import io.hhplus.tdd.point.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;


class PointControllerTest {

    private MockMvc mockMvc;

    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;

    @BeforeEach
    void setup() {
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();

        PointService service = new PointService(userPointTable, pointHistoryTable);
        PointController controller = new PointController(service);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalPointExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("포인트 조회 - 잔고 없으면 0")
    void testGetPointEmpty() throws Exception {
        mockMvc.perform(get("/point/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point", is(0)));
    }

    @Test
    @DisplayName("포인트 충전 성공")
    void testChargePoint() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point", is(1000)));
    }

    @Test
    @DisplayName("금액이 0 이하일 때 충전 실패")
    void testChargeInvalidAmount() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("0"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string("금액은 0보다 큰 정수여야 합니다."));
    }

    @Test
    @DisplayName("포인트 사용 성공")
    void testUsePoint() throws Exception {
        userPointTable.insertOrUpdate(1L, 1000);
        mockMvc.perform(patch("/point/1/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point", is(500)));
    }

    @Test
    @DisplayName("잔고 부족 시 사용 실패")
    void testUseInsufficient() throws Exception {
        userPointTable.insertOrUpdate(1L, 100);
        mockMvc.perform(patch("/point/1/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("200"))
                .andExpect(status().isConflict())
                .andExpect(content().string("잔액 부족"));
    }

    @Test
    @DisplayName("포인트 내역 조회")
    void testHistories() throws Exception {
        mockMvc.perform(get("/point/1/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    @DisplayName("충전 - 숫자가 아닌 충전 요청 400")
    void testChargeNotNumber() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("금액은 0보다 큰 정수여야 합니다."));
    }

    @Test
    @DisplayName("충전 - 공백이면 400")
    void testChargeBlank() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("금액은 0보다 큰 정수여야 합니다."));
    }

    @Test
    @DisplayName("충전 - Long.MAX_VALUE 근처에서 오버플로 금지")
    void testChargeOverflow() throws Exception {
        userPointTable.insertOrUpdate(1L, Long.MAX_VALUE - 5);
        mockMvc.perform(patch("/point/1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("10"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("허용 범위를 초과합니다."));
    }

    @Test
    @DisplayName("내역 기록 - 충전/사용 시 정확히 2건, 타입/금액 검증")
    void testHistoryCorrectness() throws Exception {
        mockMvc.perform(patch("/point/1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("1000"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/point/1/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("400"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/point/1/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].type", is("CHARGE")))
                .andExpect(jsonPath("$[0].amount", is(1000)))
                .andExpect(jsonPath("$[1].type", is("USE")))
                .andExpect(jsonPath("$[1].amount", is(400)));
    }

    @Test
    @DisplayName("포인트 충전 - 디버그용: 0원일 때 실제 응답 확인")
    void debugChargeZero() throws Exception {
        var res = mockMvc.perform(patch("/point/1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("0"))
                .andReturn().getResponse();
                
        System.out.println("DEBUG status=" + res.getStatus());
        System.out.println("DEBUG contentType=" + res.getContentType());
        System.out.println("DEBUG body=" + res.getContentAsString());
     }
}