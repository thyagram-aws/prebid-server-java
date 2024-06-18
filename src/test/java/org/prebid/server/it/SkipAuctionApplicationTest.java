package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static java.util.Collections.emptyList;

@RunWith(SpringRunner.class)
public class SkipAuctionApplicationTest extends IntegrationTest {

    @Test
    public void shouldSkipAuction() throws IOException, JSONException {
        // when
        final Response response = given(SPEC)
                .body(jsonFrom("openrtb2/skip_auction/test-auction-skip-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/skip_auction/test-auction-skip-response.json",
                response, emptyList());

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
