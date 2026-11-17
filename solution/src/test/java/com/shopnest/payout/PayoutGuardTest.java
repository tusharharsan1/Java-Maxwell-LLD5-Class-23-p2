package com.shopnest.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checks the payout problem of Class 23 (The Guarded Payout).
 * The real application runs with its in-memory H2 database, and the pretend bank is told by the test what to do.
 * Every test starts with an empty table and a healthy bank, and has a time limit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PayoutGuardTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    PayoutRepo repo;
    @Autowired
    PretendBank bank;

    @BeforeEach
    void freshStart() {
        repo.deleteAll();
        bank.reset();
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions createPayout(String sellerId, long amount) throws Exception {
        return mvc.perform(post("/payouts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellerId\":\"" + sellerId + "\",\"amount\":" + amount + "}"));
    }

    private static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions confirm(String id) throws Exception {
        return mvc.perform(post("/payouts/" + id + "/confirm"));
    }

    private ResultActions cancel(String id) throws Exception {
        return mvc.perform(post("/payouts/" + id + "/cancel"));
    }

    private PayoutStatus statusInDatabase(String id) {
        return repo.findById(UUID.fromString(id)).orElseThrow().getStatus();
    }

    /** A payout that is in the wanted status, reached only by moves that are on an arrow. */
    private static Payout payoutIn(PayoutStatus wanted) {
        Payout p = new Payout("S-1", 49900);
        switch (wanted) {
            case REQUESTED -> {
            }
            case SENT -> p.moveTo(PayoutStatus.SENT);
            case PAID -> {
                p.moveTo(PayoutStatus.SENT);
                p.moveTo(PayoutStatus.PAID);
            }
            case CANCELLED -> {
                p.moveTo(PayoutStatus.SENT);
                p.moveTo(PayoutStatus.CANCELLED);
            }
            case FAILED -> p.moveTo(PayoutStatus.FAILED);
        }
        return p;
    }

    private static boolean onAnArrow(PayoutStatus from, PayoutStatus to) {
        return (from == PayoutStatus.REQUESTED && (to == PayoutStatus.SENT || to == PayoutStatus.FAILED))
                || (from == PayoutStatus.SENT && (to == PayoutStatus.PAID || to == PayoutStatus.CANCELLED));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> everyMove() {
        return Arrays.stream(PayoutStatus.values()).flatMap(from -> Arrays.stream(PayoutStatus.values())
                .map(to -> org.junit.jupiter.params.provider.Arguments.of(from, to)));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> movesThatMustBeRefused() {
        return everyMove().filter(a -> !onAnArrow((PayoutStatus) a.get()[0], (PayoutStatus) a.get()[1]));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> movesOnAnArrow() {
        return everyMove().filter(a -> onAnArrow((PayoutStatus) a.get()[0], (PayoutStatus) a.get()[1]));
    }

    // ---------------------------------------------------------------- Task 1: the rules

    @ParameterizedTest(name = "canMoveTo: {0} to {1}")
    @MethodSource("everyMove")
    @DisplayName("Task 1: the rules say yes only for the four moves on an arrow")
    void rules(PayoutStatus from, PayoutStatus to) {
        assertThat(from.canMoveTo(to)).isEqualTo(onAnArrow(from, to));
    }

    // ---------------------------------------------------------------- Task 2: the door

    @ParameterizedTest(name = "moveTo: {0} to {1} works")
    @MethodSource("movesOnAnArrow")
    @DisplayName("Task 2: a move on an arrow changes the status")
    void doorLetsAnArrowThrough(PayoutStatus from, PayoutStatus to) {
        Payout p = payoutIn(from);
        p.moveTo(to);
        assertThat(p.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "moveTo: {0} to {1} is refused")
    @MethodSource("movesThatMustBeRefused")
    @DisplayName("Task 2: a move that is not on an arrow is refused, and the status stays")
    void doorRefusesTheRest(PayoutStatus from, PayoutStatus to) {
        Payout p = payoutIn(from);
        assertThatThrownBy(() -> p.moveTo(to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
        assertThat(p.getStatus()).isEqualTo(from);
    }

    @Test
    @DisplayName("Rule: a new payout starts as REQUESTED, and the status has no setter")
    void startsAsRequestedAndHasNoSetter() {
        assertThat(new Payout("S-1", 500).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        List<String> names = Arrays.stream(Payout.class.getMethods()).map(Method::getName).toList();
        assertThat(names).doesNotContain("setStatus");
    }

    // ---------------------------------------------------------------- Task 3: the call to the bank

    @Test
    @DisplayName("Task 3: when the bank takes it, the answer is 201 and the payout is SENT with the bank's reference")
    void bankTakesIt() throws Exception {
        ResultActions result = createPayout("S-1", 49900)
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sellerId").value("S-1"))
                .andExpect(jsonPath("$.amount").value(49900))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.bankReference").value("BANK-1"))
                .andExpect(jsonPath("$.failureReason").doesNotExist());
        String id = idOf(result);
        assertThat(bank.calls()).isEqualTo(1);
        assertThat(repo.count()).isEqualTo(1);
        Payout saved = repo.findById(UUID.fromString(id)).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PayoutStatus.SENT);
        assertThat(saved.getBankReference()).isEqualTo("BANK-1");
        assertThat(saved.getAmount()).isEqualTo(49900L);
    }

    @Test
    @DisplayName("Tasks 3 and 4: when the bank says no, the answer is a clear 502, and the payout is saved as FAILED")
    void bankSaysNo() throws Exception {
        bank.setMode("no");
        createPayout("S-2", 20000)
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.title").value("The bank could not take the payout"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("too little money")));
        assertThat(bank.calls()).as("the bank is called once, and never again for the same request").isEqualTo(1);
        assertThat(repo.count()).isEqualTo(1);
        Payout saved = repo.findAll().get(0);
        assertThat(saved.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("too little money");
        assertThat(saved.getBankReference()).isNull();
    }

    @Test
    @DisplayName("Tasks 3 and 4: when the bank does not answer, the same 502, FAILED, and still only one call")
    void bankIsSilent() throws Exception {
        bank.setMode("silent");
        createPayout("S-3", 20000)
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("The bank could not take the payout"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("did not answer")));
        assertThat(bank.calls()).as("no answer means: we do not know. We never call again by ourselves.").isEqualTo(1);
        assertThat(repo.count()).isEqualTo(1);
        Payout saved = repo.findAll().get(0);
        assertThat(saved.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("did not answer");
    }

    @Test
    @DisplayName("Rule: no database transaction is open while the bank is called")
    void noTransactionDuringTheBankCall() throws Exception {
        createPayout("S-1", 49900).andExpect(status().isCreated());
        assertThat(bank.calls()).isEqualTo(1);
        assertThat(bank.lastCallInTransaction()).isFalse();
        bank.setMode("no");
        createPayout("S-1", 49900).andExpect(status().isBadGateway());
        assertThat(bank.lastCallInTransaction()).isFalse();
    }

    @Test
    @DisplayName("a failed payout does not stop the next one: the bank works again")
    void nextPayoutAfterAFailure() throws Exception {
        bank.setMode("no");
        createPayout("S-2", 20000).andExpect(status().isBadGateway());
        bank.setMode("ok");
        createPayout("S-2", 20000).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SENT"));
        assertThat(repo.count()).isEqualTo(2);
        assertThat(repo.findAll().stream().map(Payout::getStatus))
                .containsExactlyInAnyOrder(PayoutStatus.FAILED, PayoutStatus.SENT);
    }

    @Test
    @DisplayName("an amount below 100 paise is 400: nothing is saved and the bank is not called")
    void smallAmountIs400() throws Exception {
        createPayout("S-1", 99).andExpect(status().isBadRequest());
        assertThat(repo.count()).isZero();
        assertThat(bank.calls()).isZero();
        createPayout("S-1", 100).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an empty seller is 400: nothing is saved and the bank is not called")
    void blankSellerIs400() throws Exception {
        mvc.perform(post("/payouts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellerId\":\"  \",\"amount\":49900}")).andExpect(status().isBadRequest());
        assertThat(repo.count()).isZero();
        assertThat(bank.calls()).isZero();
    }

    // ---------------------------------------------------------------- Tasks 1, 2 and 4 through the doors

    @Test
    @DisplayName("confirm moves a SENT payout to PAID, and it is saved")
    void confirmSent() throws Exception {
        String id = idOf(createPayout("S-1", 49900));
        confirm(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.bankReference").value("BANK-1"));
        assertThat(statusInDatabase(id)).isEqualTo(PayoutStatus.PAID);
    }

    @Test
    @DisplayName("cancel moves a SENT payout to CANCELLED, and it is saved")
    void cancelSent() throws Exception {
        String id = idOf(createPayout("S-1", 49900));
        cancel(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(statusInDatabase(id)).isEqualTo(PayoutStatus.CANCELLED);
    }

    @Test
    @DisplayName("Task 4: PAID is final: a second confirm and a cancel are 409 with a clear title, and nothing changes")
    void paidIsFinal() throws Exception {
        String id = idOf(createPayout("S-1", 49900));
        confirm(id).andExpect(status().isOk());
        confirm(id).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Not allowed for this payout"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("PAID")));
        cancel(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Not allowed for this payout"));
        assertThat(statusInDatabase(id)).isEqualTo(PayoutStatus.PAID);
    }

    @Test
    @DisplayName("Task 4: CANCELLED is final: confirm and a second cancel are 409")
    void cancelledIsFinal() throws Exception {
        String id = idOf(createPayout("S-1", 49900));
        cancel(id).andExpect(status().isOk());
        confirm(id).andExpect(status().isConflict());
        cancel(id).andExpect(status().isConflict());
        assertThat(statusInDatabase(id)).isEqualTo(PayoutStatus.CANCELLED);
    }

    @Test
    @DisplayName("Task 4: FAILED is final: confirm and cancel are 409")
    void failedIsFinal() throws Exception {
        bank.setMode("no");
        createPayout("S-2", 20000).andExpect(status().isBadGateway());
        String id = repo.findAll().get(0).getId().toString();
        confirm(id).andExpect(status().isConflict());
        cancel(id).andExpect(status().isConflict());
        assertThat(statusInDatabase(id)).isEqualTo(PayoutStatus.FAILED);
    }

    @Test
    @DisplayName("a REQUESTED payout (the bank has not taken it) can be neither confirmed nor cancelled: 409")
    void requestedCannotBeConfirmedOrCancelled() throws Exception {
        String id = repo.save(new Payout("S-9", 5000)).getId().toString();
        confirm(id).andExpect(status().isConflict());
        cancel(id).andExpect(status().isConflict());
        assertThat(statusInDatabase(id)).isEqualTo(PayoutStatus.REQUESTED);
    }

    // ---------------------------------------------------------------- reading (already written)

    @Test
    @DisplayName("GET gives the saved payout, and 404 for an id that does not exist")
    void getAndNotFound() throws Exception {
        String id = idOf(createPayout("S-1", 49900));
        mvc.perform(get("/payouts/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.status").value("SENT"));
        String unknown = UUID.randomUUID().toString();
        mvc.perform(get("/payouts/" + unknown)).andExpect(status().isNotFound());
        confirm(unknown).andExpect(status().isNotFound());
        cancel(unknown).andExpect(status().isNotFound());
    }
}
