package com.bankapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "app.jwt.secret=clave-de-pruebas-con-mas-de-32-caracteres",
        "spring.datasource.url=jdbc:h2:mem:bank-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class BankApiTest {

    private static final String PASSWORD = "supersecreta1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    // -- auth ----------------------------------------------------------------------------------

    @Test
    void duplicateEmailAndBadLoginAreRejected() throws Exception {
        String email = newEmail();
        register(email);

        MvcResult duplicate = send(post("/auth/register"), null, Map.of(
                "email", email.toUpperCase(), "fullName", "Otra Ana", "password", PASSWORD));
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);

        MvcResult wrongPassword = send(post("/auth/login"), null, Map.of("email", email, "password", "mala"));
        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);

        MvcResult unknown = send(post("/auth/login"), null, Map.of("email", newEmail(), "password", "x"));
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        assertThat(send(get("/accounts"), null, null).getResponse().getStatus()).isEqualTo(401);
        assertThat(send(get("/accounts"), "basura", null).getResponse().getStatus()).isEqualTo(401);
        assertThat(send(get("/health"), null, null).getResponse().getStatus()).isEqualTo(200);
    }

    // -- movimientos ---------------------------------------------------------------------------

    @Test
    void depositAndWithdraw() throws Exception {
        String token = newUserToken();
        String number = openAccount(token, "USD");

        MvcResult deposit = send(post("/accounts/" + number + "/deposit"), token, Map.of("amount", "100.50"));
        assertThat(deposit.getResponse().getStatus()).isEqualTo(201);
        assertThat(number(deposit, "$.balanceAfter")).isEqualTo(100.5);

        MvcResult withdraw = send(post("/accounts/" + number + "/withdraw"), token, Map.of("amount", "40.25"));
        assertThat(number(withdraw, "$.balanceAfter")).isEqualTo(60.25);
        assertThat(balance(token, number)).isEqualTo(60.25);
    }

    @Test
    void cannotOverdraw() throws Exception {
        String token = newUserToken();
        String number = openAccount(token, "USD");
        send(post("/accounts/" + number + "/deposit"), token, Map.of("amount", "10"));

        MvcResult result = send(post("/accounts/" + number + "/withdraw"), token, Map.of("amount", "10.01"));
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(balance(token, number)).isEqualTo(10.0);
    }

    @Test
    void invalidAmountsAreRejected() throws Exception {
        String token = newUserToken();
        String number = openAccount(token, "USD");

        for (String bad : new String[] {"0", "-5", "1.999", "abc"}) {
            MvcResult result = send(post("/accounts/" + number + "/deposit"), token, Map.of("amount", bad));
            assertThat(result.getResponse().getStatus()).as("monto %s", bad).isIn(400, 422);
        }
        assertThat(balance(token, number)).isEqualTo(0.0);
    }

    // -- transferencias ------------------------------------------------------------------------

    @Test
    void transferBetweenUsers() throws Exception {
        String ana = newUserToken();
        String luis = newUserToken();
        String a = openAccount(ana, "USD");
        String b = openAccount(luis, "USD");
        send(post("/accounts/" + a + "/deposit"), ana, Map.of("amount", "200"));

        MvcResult transfer = send(post("/transfers"), ana, Map.of(
                "sourceNumber", a, "destinationNumber", b, "amount", "75.00", "description", "renta"));
        assertThat(transfer.getResponse().getStatus()).isEqualTo(201);
        assertThat((String) JsonPath.read(transfer.getResponse().getContentAsString(), "$.kind"))
                .isEqualTo("transfer_out");
        assertThat(balance(ana, a)).isEqualTo(125.0);
        assertThat(balance(luis, b)).isEqualTo(75.0);

        MvcResult history = send(get("/accounts/" + b + "/transactions"), luis, null);
        String body = history.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$[0].kind")).isEqualTo("transfer_in");
        assertThat((String) JsonPath.read(body, "$[0].counterpartyNumber")).isEqualTo(a);
        assertThat((String) JsonPath.read(body, "$[0].reference"))
                .isEqualTo(JsonPath.read(transfer.getResponse().getContentAsString(), "$.reference"));
    }

    @Test
    void transferRulesAreEnforced() throws Exception {
        String ana = newUserToken();
        String luis = newUserToken();
        String a = openAccount(ana, "USD");
        String b = openAccount(luis, "USD");
        String eur = openAccount(luis, "EUR");
        send(post("/accounts/" + a + "/deposit"), ana, Map.of("amount", "50"));

        assertThat(transferStatus(ana, a, a, "1")).isEqualTo(422);               // misma cuenta
        assertThat(transferStatus(ana, a, eur, "1")).isEqualTo(422);             // monedas distintas
        assertThat(transferStatus(ana, a, "999999999999", "1")).isEqualTo(404);  // destino inexistente
        assertThat(transferStatus(ana, a, b, "50.01")).isEqualTo(409);           // sin fondos
        assertThat(transferStatus(luis, b, a, "1")).isEqualTo(409);              // luis no tiene saldo
        assertThat(transferStatus(luis, a, b, "1")).isEqualTo(404);              // luis no es dueño de a
        assertThat(balance(ana, a)).isEqualTo(50.0);
    }

    // -- privacidad e historial ----------------------------------------------------------------

    @Test
    void accountsArePrivate() throws Exception {
        String ana = newUserToken();
        String luis = newUserToken();
        String number = openAccount(ana, "USD");

        assertThat(send(get("/accounts/" + number), luis, null).getResponse().getStatus()).isEqualTo(404);
        assertThat(send(post("/accounts/" + number + "/deposit"), luis, Map.of("amount", "5"))
                .getResponse().getStatus()).isEqualTo(404);
        assertThat(send(get("/accounts"), luis, null).getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    void historyIsPaginatedNewestFirst() throws Exception {
        String token = newUserToken();
        String number = openAccount(token, "USD");
        for (int i = 0; i < 5; i++) {
            send(post("/accounts/" + number + "/deposit"), token, Map.of("amount", "1"));
        }

        MvcResult page = send(get("/accounts/" + number + "/transactions?page=1&size=2"), token, null);
        String body = page.getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.length()")).isEqualTo(2);
        assertThat(((Number) JsonPath.read(body, "$[0].balanceAfter")).doubleValue()).isEqualTo(3.0);
        assertThat(((Number) JsonPath.read(body, "$[1].balanceAfter")).doubleValue()).isEqualTo(2.0);
    }

    // -- helpers -------------------------------------------------------------------------------

    private MvcResult send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request).andReturn();
    }

    private static String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private void register(String email) throws Exception {
        MvcResult result = send(post("/auth/register"), null, Map.of(
                "email", email, "fullName", "Ana Pérez", "password", PASSWORD));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    private String newUserToken() throws Exception {
        String email = newEmail();
        register(email);
        MvcResult login = send(post("/auth/login"), null, Map.of("email", email, "password", PASSWORD));
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private String openAccount(String token, String currency) throws Exception {
        MvcResult result = send(post("/accounts"), token, Map.of("currency", currency));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.number");
    }

    private double balance(String token, String number) throws Exception {
        return number(send(get("/accounts/" + number), token, null), "$.balance");
    }

    private int transferStatus(String token, String from, String to, String amount) throws Exception {
        return send(post("/transfers"), token, Map.of(
                "sourceNumber", from, "destinationNumber", to, "amount", amount)).getResponse().getStatus();
    }

    private static double number(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.doubleValue();
    }
}
